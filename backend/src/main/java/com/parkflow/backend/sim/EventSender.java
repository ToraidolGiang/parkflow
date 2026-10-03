package com.parkflow.backend.sim;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventSender {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper;
    private final String url,key;
    public EventSender(ObjectMapper mapper,@Value("${parkflow.sim.events-url:http://127.0.0.1:${server.port:8080}/api/events}") String url,
                       com.parkflow.backend.common.SimulatorKey simulatorKey) {this.mapper=mapper;this.url=url;this.key=simulatorKey.value();}
    /** Gửi một lô sự kiện (tối đa 500 mỗi lần gọi); dùng để tua nhanh. @return số sự kiện được nhận mới. */
    public int sendBatch(java.util.List<ReplayLog.Event> events) {
        int accepted=0;
        for(int from=0;from<events.size();from+=500) {
            var chunk=events.subList(from,Math.min(events.size(),from+500)).stream().map(ReplayLog.Event::payload).toList();
            var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Content-Type","application/json")
                    .header("X-Api-Key",key).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(chunk))).build();
            boolean done=false;
            for(int attempt=0;attempt<3&&!done;attempt++) {
                try {
                    var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                    if(response.statusCode()==429||response.statusCode()>=500) continue;
                    if(response.statusCode()!=200) throw new IllegalStateException("Ingest HTTP "+response.statusCode());
                    var body=mapper.readTree(response.body());
                    if(!body.path("rejected").isArray()||!body.path("rejected").isEmpty()) throw new IllegalStateException("Ingest rejected events");
                    accepted+=body.path("accepted").asInt();done=true;
                } catch(java.io.IOException e) { /* thử lại cùng lô: sự kiện có mã nên không bị tính trùng */ }
                  catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException("Replay interrupted");}
            }
            if(!done) throw new IllegalStateException("Ingest unavailable after 3 attempts");
        }
        return accepted;
    }

    /** 1 accepted or 0 duplicate. Permanent rejection stops replay instead of losing an event. */
    public int send(ReplayLog.Event event) {
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
                .header("X-Api-Key",key).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(event.payload()))).build();
        for(int attempt=0;attempt<3;attempt++) {
            try {
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()==429||response.statusCode()>=500) continue;
                if(response.statusCode()!=200) throw new IllegalStateException("Ingest HTTP "+response.statusCode());
                var body=mapper.readTree(response.body());
                if(!body.path("rejected").isArray()||!body.path("rejected").isEmpty()
                        ||!body.path("accepted").isIntegralNumber()||!body.path("duplicate").isIntegralNumber()
                        ||body.path("accepted").asInt()<0||body.path("duplicate").asInt()<0
                        ||body.path("accepted").asInt()+body.path("duplicate").asInt()!=1)
                    throw new IllegalStateException("Ingest rejected event or returned invalid acknowledgement");
                return body.path("accepted").asInt();
            } catch(java.io.IOException e) { /* Retry with the same id, including ambiguous delivery. */ }
              catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException("Replay interrupted");}
        }
        throw new IllegalStateException("Ingest unavailable after 3 attempts");
    }
}
