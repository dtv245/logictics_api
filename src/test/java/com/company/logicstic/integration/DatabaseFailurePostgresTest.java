package com.company.logicstic.integration;

import com.company.logicstic.exception.GlobalExceptionHandler;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL metadata, rolled-back deadlocks and serialization failures in an isolated schema. */
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class DatabaseFailurePostgresTest {
    private static String schema;
    private static Connection connect() throws SQLException {
        var c=DriverManager.getConnection(System.getenv("TASK_DB_URL"),System.getenv().getOrDefault("TASK_DB_USER","postgres"),System.getenv("TASK_DB_PASSWORD"));
        if(schema!=null)try(var s=c.createStatement()){s.execute("set search_path to "+schema);s.execute("set statement_timeout='10s'");}
        return c;
    }
    @BeforeAll static void setup() throws Exception {
        String name="error_contract_"+UUID.randomUUID().toString().replace("-","");
        try(var c=connect();var s=c.createStatement()) {s.execute("create schema "+name);}
        schema=name;
        try(var c=connect();var s=c.createStatement()) {
            s.execute("create table records(id int primary key, number text not null, parent int, positive int check(positive>0), constraint ix_trucks_number unique(number), constraint fk_trucks_employees_main_driver_id foreign key(parent) references records(id))");
            s.execute("insert into records values(1,'ONE',null,1),(2,'TWO',null,1)");
        }
    }
    @AfterAll static void cleanup() throws Exception { if(schema!=null)try(var c=connect();var s=c.createStatement()){s.execute("drop schema "+schema+" cascade");} }
    private void check(SQLException cause,int status,String field) {
        var response=new GlobalExceptionHandler().handleUnexpectedException(new DataIntegrityViolationException("private-row",cause),new MockHttpServletRequest("POST","/api/trucks"));
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        if(field!=null) assertThat(response.getBody().errors().getFirst().field()).isEqualTo(field);
        assertThat(response.getBody().toString()).doesNotContain("ix_trucks_number","fk_trucks_employees_main_driver_id","private-row");
    }
    @Test void uniqueForeignKeyNotNullAndCheckUseActualStructuredMetadata() throws Exception {
        for(var fixture:List.of(new String[]{"insert into records values(3,'ONE',null,1)","409","number"},
                new String[]{"insert into records values(3,'THREE',99,1)","409","mainDriverId"},
                new String[]{"insert into records values(3,null,null,1)","500",""},
                new String[]{"insert into records values(3,'THREE',null,-1)","500",""})) {
            try(var c=connect();var s=c.createStatement()) {
                SQLException e=org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,()->s.execute(fixture[0]));
                check(e,Integer.parseInt(fixture[1]),fixture[2].isEmpty()?null:fixture[2]);
            }
        }
    }
    @Test void serializationFailureIsRetryableAfterRollback() throws Exception {
        try(var a=connect();var b=connect()) {
            a.setAutoCommit(false);b.setAutoCommit(false);
            a.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);b.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try(var x=a.createStatement();var y=b.createStatement()) {
                x.executeQuery("select positive from records where id=1");y.executeQuery("select positive from records where id=1");
                x.executeUpdate("update records set positive=positive+1 where id=1");a.commit();
                SQLException e=org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,()->y.executeUpdate("update records set positive=positive+1 where id=1"));
                assertThat(e.getSQLState()).isEqualTo("40001");b.rollback();check(e,503,null);
            }
        }
    }
    @Test void deadlockVictimRollsBackAndOtherTransactionCompletesOnce() throws Exception {
        int before;
        try(var c=connect();var s=c.createStatement();var rows=s.executeQuery("select sum(positive) from records")){rows.next();before=rows.getInt(1);}
        var ready=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> first=()->deadlockSide(1,2,ready),second=()->deadlockSide(2,1,ready);
            var a=pool.submit(first);var b=pool.submit(second);
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("COMMITTED","40P01");
        }
        try(var c=connect();var s=c.createStatement();var rows=s.executeQuery("select sum(positive) from records")){rows.next();assertThat(rows.getInt(1)).isEqualTo(before+2);}
    }
    @Test void concurrentUniqueInsertCreatesExactlyOneRecord() throws Exception {
        var start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->uniqueSide(10,start));var b=pool.submit(()->uniqueSide(11,start));
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("CREATED","23505");
        }
        try(var c=connect();var s=c.createStatement();var rows=s.executeQuery("select count(*) from records where number='RACE'")){rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
    }
    private String uniqueSide(int id,CyclicBarrier start) throws Exception {
        try(var c=connect();var s=c.createStatement()) {
            start.await(5,TimeUnit.SECONDS);
            try{s.executeUpdate("insert into records values("+id+",'RACE',null,1)");return "CREATED";}
            catch(SQLException e){assertThat(e.getSQLState()).isEqualTo("23505");check(e,409,"number");return e.getSQLState();}
        }
    }
    private String deadlockSide(int first,int second,CyclicBarrier ready) throws Exception {
        try(var c=connect();var s=c.createStatement()) {
            c.setAutoCommit(false);
            s.executeUpdate("update records set positive=positive+1 where id="+first);ready.await(5,TimeUnit.SECONDS);
            try {s.executeUpdate("update records set positive=positive+1 where id="+second);c.commit();return "COMMITTED";}
            catch(SQLException e){c.rollback();assertThat(e.getSQLState()).isEqualTo("40P01");check(e,503,null);return e.getSQLState();}
        }
    }
}
