package com.company.logicstic.integration.lark.base;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties={"spring.config.import=","app.tenancy.enabled=false","app.lark.base.enabled=false",
    "spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration/tenant"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class LarkSyncOpenApiPostgresTest {
  @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));
    p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));
    p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));
  }
  @Autowired WebApplicationContext context;
  @Test void exportsActualSpringdocContractForTypeGenerationWithoutCallingLark() throws Exception {
    var response=MockMvcBuilders.webAppContextSetup(context).build().perform(get("/v3/api-docs")).andReturn().getResponse();
    assertEquals(200,response.getStatus());
    var json=JsonMapper.builder().build();var document=json.readTree(response.getContentAsString());
    var paths=json.createObjectNode();
    document.path("paths").properties().forEach(e->{if(e.getKey().startsWith("/api/lark/base/")) paths.set(e.getKey(),e.getValue());});
    assertTrue(paths.has("/api/lark/base/loads/{id}/sync"));assertTrue(paths.has("/api/lark/base/missing"));
    var schemas=json.createObjectNode();var queue=new ArrayDeque<String>();var seen=new HashSet<String>();
    collect(paths,queue);
    while(!queue.isEmpty()) {
      String name=queue.remove();if(!seen.add(name)) continue;
      var schema=document.path("components").path("schemas").get(name);assertNotNull(schema,name);
      schemas.set(name,schema);collect(schema,queue);
    }
    var output=json.createObjectNode();output.put("openapi",document.path("openapi").asString());
    output.set("info",document.path("info"));output.set("paths",paths);output.putObject("components").set("schemas",schemas);
    String target=System.getProperty("lark.openapi.output","/tmp/lark-base-runtime-openapi.json");
    Files.writeString(Path.of(target),json.writerWithDefaultPrettyPrinter().writeValueAsString(output)+"\n");
  }
  static void collect(tools.jackson.databind.JsonNode node,Queue<String> queue) {
    if(node.isObject()) node.properties().forEach(e->{if(e.getKey().equals("$ref")) queue.add(e.getValue().asString().replace("#/components/schemas/",""));else collect(e.getValue(),queue);});
    else if(node.isArray()) node.forEach(n->collect(n,queue));
  }
}
