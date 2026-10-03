package com.parkflow.backend.plan;

import com.parkflow.backend.approval.PlanVersionService;
import com.parkflow.backend.common.*;
import com.parkflow.backend.forecast.ForecastService;
import com.parkflow.backend.repository.LotRepository;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import static com.parkflow.backend.plan.PlanAllocator.*;

@Service
public class PlanScheduler {
    private final LotRepository lots;
    private final ForecastService forecasts;
    private final PlanVersionService versions;
    private final ParkFlowClock clock;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public PlanScheduler(LotRepository lots,ForecastService forecasts,PlanVersionService versions,
            ParkFlowClock clock,JdbcTemplate jdbc,ObjectMapper mapper) {
        this.lots=lots; this.forecasts=forecasts; this.versions=versions;
        this.clock=clock; this.jdbc=jdbc; this.mapper=mapper;
    }

    @Scheduled(fixedDelay=60_000, initialDelay=60_000)
    public void refresh() {
        var now=clock.read().now();
        if(now.toLocalTime().isBefore(LocalTime.of(6,0)) || now.toLocalTime().isAfter(LocalTime.of(13,0))) return;
        var configured=lots.findAll();
        if(configured.isEmpty()) return;
        List<LotInput> inputs=new ArrayList<>();
        Map<String,PlanHysteresis.LotSnapshot> snapshots=new LinkedHashMap<>();
        for(var lot:configured) {
            ForecastService.Forecast forecast;
            try { forecast=forecasts.forecast(lot.getLotId(),now.toLocalDate()); }
            catch(ApiException ex) { if("NO_HISTORY".equals(ex.code())) return; throw ex; }
            int peak=(int)Math.round(forecast.points().stream().mapToDouble(ForecastService.Point::count).max().orElseThrow());
            inputs.add(new LotInput(lot.getLotId(),lot.getName(),lot.getCapacity(),peak,lot.getGate(),List.of()));
            snapshots.put(lot.getLotId(),new PlanHysteresis.LotSnapshot(peak,lot.getCapacity()));
        }
        // Tải quan sát 15 phút gần nhất: không giả định cổng rỗng khi chưa có dự báo lưu lượng cổng.
        var gates=jdbc.query("""
                SELECT g.gate,g.name,g.capacity_per_15,g.normal_open,COUNT(e.event_id) AS inflow
                FROM forecast_gates g LEFT JOIN lots l ON l.gate=g.gate
                LEFT JOIN events e ON e.lot_id=l.lot_id AND e.direction='in'
                    AND e.ts > ? AND e.ts <= ? AND e.historical=false
                GROUP BY g.gate,g.name,g.capacity_per_15,g.normal_open ORDER BY g.gate
                """,(rs,n)-> {
                    double usage=rs.getDouble("inflow")/rs.getInt("capacity_per_15");
                    return new GateInput(rs.getString("gate"),rs.getString("name"),rs.getInt("capacity_per_15"),
                            usage,usage>0.9,LocalTime.parse(rs.getString("normal_open")));
                },now.minusMinutes(15),now);
        if(gates.isEmpty()) return;
        Set<String> gateIds=new HashSet<>(); gates.forEach(g->gateIds.add(g.id()));
        if(inputs.stream().anyMatch(l->!gateIds.contains(l.gate()))) return;
        var result=allocate(inputs,gates,Set.of(),1);
        ObjectNode body=mapper.createObjectNode().put("shortfall",result.shortfall())
                .put("reason","Điều hướng theo đỉnh dự báo; giới hạn cổng theo lưu lượng quan sát 15 phút gần nhất");
        LocalTime from=now.toLocalTime().withSecond(0).withNano(0);
        int minute=(from.getMinute()/15)*15; from=from.withMinute(minute);
        LocalTime to=from.plusMinutes(15);
        body.putObject("window").put("from",from.toString()).put("to",to.toString());
        var before=body.putArray("before"); var after=body.putArray("after");
        for(var lot:inputs) {
            before.addObject().put("lotId",lot.id()).put("count",lot.peak()).put("pct",100.0*lot.peak()/lot.capacity());
            after.addObject().put("lotId",lot.id()).put("count",result.after().get(lot.id()))
                    .put("pct",100.0*result.after().get(lot.id())/lot.capacity());
        }
        var moves=body.putArray("moves");
        for(var move:result.moves()) moves.addObject().put("from",move.from()).put("to",move.to())
                .put("gate",move.gate()).put("vehicles",move.vehicles()).put("quota",move.quota()).put("used",0);
        body.set("excluded",mapper.valueToTree(result.excluded()));
        var actions=body.putArray("gateActions");
        Set<String> used=new HashSet<>(); result.moves().forEach(m->used.add(m.gate()));
        for(var gate:gates) if(used.contains(gate.id())) actions.addObject().put("gate",gate.id())
                .put("openFrom",from.minusMinutes(30).toString()).put("normalOpen",gate.normalOpen().toString())
                .put("extraGuard",from.minusMinutes(30)+"–"+to);
        var envelope=body.putObject("envelope").put("maxMoveTotal",150).put("maxTargetPct",1.0);
        envelope.set("allowedDest",mapper.valueToTree(result.allowedDest()));
        if(result.shortfall()>0) body.putArray("shortfallOptions").addObject().put("code","ACCEPT_HIGHER")
                .put("label","Chấp nhận bãi nguồn cao hơn mục tiêu; thiếu "+result.shortfall()+" chỗ");
        versions.publish(now.toLocalDate(),body,new PlanHysteresis.Snapshot(snapshots,result.shortfall()));
    }
}