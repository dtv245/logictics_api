package com.company.logicstic.integration.lark.base;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.LarkFailurePolicy;
import com.company.logicstic.integration.lark.config.LarkProperties;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LarkSyncGateway {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final LarkSyncMapping mapping;
  private final LarkBaseTokenProvider tokens;
  private final LarkFailurePolicy failures;
  private final String baseUrl;
  private final RestTemplate http;
  @Autowired
  public LarkSyncGateway(LarkSyncMapping mapping,LarkBaseTokenProvider tokens,LarkProperties properties,LarkFailurePolicy failures) {
    this(mapping,tokens,properties,failures,boundedHttp());
  }
  public LarkSyncGateway(LarkSyncMapping mapping,LarkBaseTokenProvider tokens,LarkProperties properties,LarkFailurePolicy failures,RestTemplate http) {
    this.mapping=mapping;this.tokens=tokens;this.failures=failures;this.baseUrl=properties.baseUrl();this.http=http;
  }
  private static RestTemplate boundedHttp() {
    var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);factory.setReadTimeout(10000);
    return new RestTemplate(factory);
  }
  private Map<String,Object> request(String domain,String suffix,HttpMethod method,Object body) {
    mapping.validate(domain);
    String url=baseUrl.replaceAll("/+$","")+"/open-apis/bitable/v1/apps/"+mapping.configured("LARK_BASE_APP_TOKEN")
        +"/tables/"+mapping.table(domain)+"/records"+suffix;
    var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth(tokens.getTenantAccessToken());
    try {
      var response=http.exchange(url,method,new HttpEntity<>(body,headers),String.class);
      var json=JSON.readTree(response.getBody());
      int code=json.path("code").asInt(-1);
      if(code==1254043) throw new ApiException(HttpStatus.NOT_FOUND,"LARK_BASE_RECORD_MISSING","LARK_BASE_RECORD_MISSING");
      if(code!=0) throw failures.body(code,LarkFailurePolicy.Credential.BASE,response.getHeaders());
      return LarkSyncMapping.read(json.path("data").toString());
    } catch(RestClientResponseException e) {
      if(e.getStatusCode().value()==404) throw new ApiException(HttpStatus.NOT_FOUND,"LARK_BASE_RECORD_MISSING","LARK_BASE_RECORD_MISSING");
      throw failures.transport(e,LarkFailurePolicy.Credential.BASE);
    } catch(RestClientException e) { throw failures.transport(e,LarkFailurePolicy.Credential.BASE); }
    catch(tools.jackson.core.JacksonException | IllegalArgumentException e) { throw failures.invalidResponse(); }
  }
  @SuppressWarnings("unchecked")
  private LarkBaseRecord record(Object value) {
    if(!(value instanceof Map<?,?> m) || !(m.get("record_id") instanceof String id) || id.isBlank()
        || !(m.get("fields") instanceof Map<?,?>)) throw failures.invalidResponse();
    return LarkBaseRecord.of(id,(Map<String,Object>)m.get("fields"));
  }
  public LarkBaseRecord get(String domain,String id) {
    return record(request(domain,"/"+path(id),HttpMethod.GET,null).get("record"));
  }
  public LarkBaseRecord create(String domain,Map<String,Object> fields,UUID token) {
    return record(request(domain,"?client_token="+token+"&ignore_consistency_check=false",HttpMethod.POST,Map.of("fields",fields)).get("record"));
  }
  public LarkBaseRecord update(String domain,String id,Map<String,Object> fields) {
    return record(request(domain,"/"+path(id)+"?ignore_consistency_check=false",HttpMethod.PUT,Map.of("fields",fields)).get("record"));
  }
  public List<LarkBaseRecord> find(String domain,UUID id) {
    Map<String,Object> filter=Map.of("conjunction","and","conditions",List.of(Map.of("field_name",mapping.identityField(domain),"operator","is","value",List.of(id.toString()))));
    var data=request(domain,"/search?page_size=2",HttpMethod.POST,Map.of("filter",filter));
    if(Boolean.TRUE.equals(data.get("has_more"))) throw LarkSyncMapping.error("LARK_BASE_DUPLICATE_ID");
    Object items=data.get("items");
    if(items==null) return List.of();
    if(!(items instanceof List<?> list)) throw failures.invalidResponse();
    return list.stream().map(this::record).toList();
  }
  private String path(String id) {
    if(id==null || !id.matches("[A-Za-z0-9_-]{1,100}")) throw LarkSyncMapping.error("LARK_BASE_RECORD_ID_INVALID");
    return id;
  }
  public <T> T retry(Supplier<T> call) {
    for(int attempt=0;;attempt++) {
      try { return call.get(); } catch(ApiException failure) {
        if(attempt>=2 || !(failure.getStatus().is5xxServerError() || failure.getStatus().value()==429)) throw failure;
        long delay=100L*(1L<<attempt);
        Object wait=failure.getParams().get("retryAfterSeconds");
        if(wait instanceof Number n) {
          if(n.longValue()>30) throw failure; // Let the operator retry later; never violate Retry-After.
          delay=Math.max(delay,n.longValue()*1000);
        }
        try { Thread.sleep(delay); } catch(InterruptedException interrupted) {
          Thread.currentThread().interrupt();throw LarkSyncMapping.error("LARK_SYNC_INTERRUPTED");
        }
      }
    }
  }
}

