package com.company.logicstic.service.rating;

import com.company.logicstic.exception.BadRequestException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class EiaFuelIndexProviderTest {
    private final LocalDate date=LocalDate.of(2026,1,12);
    private String body(String period,String units,String series) {
        return "{\"apiVersion\":\"2.1.test\",\"response\":{\"data\":[{\"period\":\""+period
                +"\",\"series\":\""+series+"\",\"value\":\"3.123\",\"units\":\""+units
                +"\"}]},\"request\":{\"params\":{\"api_key\":\"secret-fixture\"}}}";
    }
    private EiaFuelIndexProvider provider(HttpServer server,String key) {
        return new EiaFuelIndexProvider(JsonMapper.builder().build(),key,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"),HttpClient.newHttpClient());
    }
    @Test void exactSeriesRegionWeeklyEndDateAndCredentialFreeMetadata() throws Exception {
        var request=new AtomicReference<String>(); var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{request.set(e.getRequestURI().getRawQuery()); byte[] b=body("2026-01-12","$/GAL",EiaFuelIndexProvider.seriesFor("CALIFORNIA")).getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();
        try {
            var rows=provider(server,"secret-fixture").observations("CALIFORNIA",date);
            assertEquals(1,rows.size()); var row=rows.getFirst(); assertEquals(date,row.observationDate());
            assertEquals("EMD_EPD2DXL0_PTE_SCA_DPG",row.seriesIdentifier()); assertEquals("2.1.test",row.providerVersion());
            assertTrue(row.contentHash().matches("[0-9a-f]{64}"));assertFalse(row.toString().contains("secret-fixture"));
            assertTrue(request.get().contains("frequency=weekly"));assertTrue(request.get().contains("end=2026-01-12"));
        } finally {server.stop(0);}
    }
    @Test void missingCredentialMakesNoNetworkRequestAndUnsupportedRegionHasNoFallback() {
        var p=new EiaFuelIndexProvider(JsonMapper.builder().build(),"");
        assertEquals("FUEL_INDEX_UNAVAILABLE",assertThrows(BadRequestException.class,()->p.observations("US",date)).getCode());
        assertEquals("INVALID_RATE_POLICY",assertThrows(BadRequestException.class,()->p.observations("UNKNOWN",date)).getCode());
        for(String region:java.util.List.of("US","PADD1","PADD1A","PADD1B","PADD1C","PADD2","PADD3","PADD4","PADD5","CALIFORNIA"))assertTrue(EiaFuelIndexProvider.seriesFor(region).contains("EPD2DXL0"));
    }
    @Test void futureBadUnitWrongSeriesMalformedAndProviderErrorFailClosed() throws Exception {
        for(String b:java.util.List.of(body("2026-01-13","$/GAL",EiaFuelIndexProvider.seriesFor("US")),body("2026-01-12","EUR/L",EiaFuelIndexProvider.seriesFor("US")),body("2026-01-12","$/GAL",EiaFuelIndexProvider.seriesFor("PADD1")),"not json","{\"error\":\"bad key\"}")) {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",e->{byte[] bytes=b.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
            try {var ex=assertThrows(BadRequestException.class,()->provider(server,"secret-fixture").observations("US",date));assertEquals("FUEL_INDEX_UNAVAILABLE",ex.getCode());assertFalse(ex.getMessage().contains("secret-fixture"));}
            finally {server.stop(0);}
        }
    }
}
