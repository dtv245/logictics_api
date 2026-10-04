package com.company.logicstic.service.rating;

import com.company.logicstic.dto.rating.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class RatingFingerprintServiceTest {
    private final RatingFingerprintService hashes=new RatingFingerprintService(JsonMapper.builder().build());
    @Test void canonicalNumbersAndObjectOrderDoNotChangeHashButRealInputDoes(){
        var a=new LinkedHashMap<String,Object>();a.put("b",new BigDecimal("1.000"));a.put("a",2);
        var b=new LinkedHashMap<String,Object>();b.put("a",2);b.put("b",BigDecimal.ONE);
        assertEquals(hashes.hash(a),hashes.hash(b));assertNotEquals(hashes.hash(a),hashes.hash(Map.of("a",2,"b",2)));
    }
    @Test void commandNormalizesCurrencyAndSelectionOrderButNotCorrectionIntent(){
        var load=UUID.randomUUID();var one=UUID.randomUUID();var two=UUID.randomUUID();
        var a=new RatingPreviewRequest(null,null,null,null,null,null,"usd","agreement",null,null,List.of(one,two));
        var b=new RatingPreviewRequest(null,null,null,null,null,null," USD ","agreement",null,null,List.of(two,one));
        var x=new RatingAcceptRequest("key",a,"a".repeat(64),"b".repeat(64),null,null,null);
        var y=new RatingAcceptRequest("key",b,"a".repeat(64),"b".repeat(64),null,null,null);
        assertEquals(hashes.command(load,x),hashes.command(load,y));
        assertNotEquals(hashes.command(load,x),hashes.command(load,new RatingAcceptRequest("key",b,"a".repeat(64),"b".repeat(64),UUID.randomUUID(),"CORRECTION","approved")));
    }
}
