package com.parkflow.backend.approval;

import com.parkflow.backend.common.ApiException;
import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.plan.PlanHysteresis;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PlanVersionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ParkFlowClock clock;

    public PlanVersionService(JdbcTemplate jdbc, ObjectMapper mapper, ParkFlowClock clock) {
        this.jdbc=jdbc; this.mapper=mapper; this.clock=clock;
    }

    public enum Status { draft, pending_approval, approved, auto_applied }
    public record Version(long version, LocalDate date, Status status, ObjectNode body,
            PlanHysteresis.Snapshot snapshot, OffsetDateTime createdAt) {}

    @Transactional
    public Version create(LocalDate date, ObjectNode body, PlanHysteresis.Snapshot snapshot) {
        Objects.requireNonNull(date); Objects.requireNonNull(body); Objects.requireNonNull(snapshot);
        lock(date);
        var old=latest(date);
        if(old.isPresent() && !PlanHysteresis.shouldReplace(old.get().snapshot(),snapshot)) return old.get();
        Long id=jdbc.queryForObject("""
                INSERT INTO plan_versions(target_date,status,body,snapshot,created_at)
                VALUES (?,'draft',?::jsonb,?::jsonb,?) RETURNING version
                """,Long.class,date,mapper.writeValueAsString(body),mapper.writeValueAsString(snapshot),clock.read().now());
        jdbc.update("UPDATE plan_state SET latest_version=? WHERE target_date=?",id,date);
        return find(id).orElseThrow();
    }

    public Optional<Version> find(long version) {
        return jdbc.query("SELECT * FROM plan_versions WHERE version=?",(rs,n)->new Version(
                rs.getLong("version"),rs.getObject("target_date",LocalDate.class),Status.valueOf(rs.getString("status")),
                (ObjectNode)mapper.readTree(rs.getString("body")),
                mapper.readValue(rs.getString("snapshot"),PlanHysteresis.Snapshot.class),
                rs.getObject("created_at",OffsetDateTime.class).atZoneSameInstant(ParkFlowClock.ZONE).toOffsetDateTime()),version)
                .stream().findFirst();
    }

    public Optional<Version> latest(LocalDate date) { return pointed(date,"latest_version"); }
    public Optional<Version> applied(LocalDate date) { return pointed(date,"applied_version"); }

    private Optional<Version> pointed(LocalDate date,String column) {
        var ids=jdbc.query("SELECT " + column + " FROM plan_state WHERE target_date=?",
                (rs,n)->rs.getObject(1,Long.class),date);
        return ids.isEmpty() || ids.get(0)==null ? Optional.empty() : find(ids.get(0));
    }

    private void lock(LocalDate date) {
        jdbc.update("INSERT INTO plan_state(target_date) VALUES (?) ON CONFLICT DO NOTHING",date);
        jdbc.queryForObject("SELECT target_date FROM plan_state WHERE target_date=? FOR UPDATE",LocalDate.class,date);
    }

    /** Chuyển trạng thái nội bộ; lớp duyệt phải kiểm tra phương án/khung trước khi áp dụng. */
    @Transactional
    public Version transition(long version, Status next, String approvedBy) {
        var initial=find(version).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NO_PLAN","Không có kế hoạch"));
        lock(initial.date());
        var current=find(version).orElseThrow();
        if(latest(current.date()).orElseThrow().version()!=version) throw stale();
        boolean pending=current.status()==Status.draft && next==Status.pending_approval;
        boolean apply=(current.status()==Status.draft || current.status()==Status.pending_approval)
                && (next==Status.approved || next==Status.auto_applied);
        if(!pending && !apply) throw stale();
        if(apply && (approvedBy==null || approvedBy.isBlank())) throw new IllegalArgumentException("Approver required");
        jdbc.update("UPDATE plan_versions SET status=?, approved_by=?, approved_at=? WHERE version=?",
                next.name(),apply?approvedBy:null,apply?clock.read().now():null,version);
        if(apply) jdbc.update("UPDATE plan_state SET applied_version=? WHERE target_date=?",version,current.date());
        return find(version).orElseThrow();
    }

    private ApiException stale() { return new ApiException(HttpStatus.CONFLICT,"PLAN_STALE","Kế hoạch đã đổi hoặc đã được duyệt"); }
    public ObjectNode current() {
        LocalDate date=clock.read().now().toLocalDate();
        var latest=latest(date).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NO_PLAN","Chưa có kế hoạch"));
        var active=applied(date);
        ObjectNode result=view(active.orElse(latest));
        if(active.isPresent() && active.get().version()!=latest.version()) result.set("pending",view(latest));
        else result.putNull("pending");
        return result;
    }

    private ObjectNode view(Version version) {
        ObjectNode body=version.body().deepCopy();
        body.remove("_alternatives");
        body.put("version",version.version());
        body.put("status",version.status().name());
        body.put("createdAt",version.createdAt().toString());
        return body;
    }

    @Transactional
    public Version approve(long version,String approvedBy,String option) {
        if(approvedBy==null || approvedBy.isBlank()) throw new IllegalArgumentException("Approver required");
        var initial=find(version).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NO_PLAN","Không có kế hoạch"));
        lock(initial.date());
        var current=find(version).orElseThrow();
        if(latest(current.date()).orElseThrow().version()!=version
                || current.status()==Status.approved || current.status()==Status.auto_applied) throw stale();
        ObjectNode body=current.body().deepCopy();
        if(body.path("shortfall").asInt()>0) {
            boolean offered=false;
            for(var item:body.path("shortfallOptions")) if(item.path("code").asString().equals(option)) offered=true;
            if(!offered) throw new IllegalArgumentException("Choose an offered shortfall option");
            if(!"ACCEPT_HIGHER".equals(option)) {
                var resolved=body.path("_alternatives").path(option);
                if(!resolved.isObject() || resolved.path("shortfall").asInt(-1)!=0) {
                    throw new IllegalArgumentException("Resolved alternative unavailable");
                }
                body=(ObjectNode)resolved.deepCopy();
            }
            body.put("selectedOption",option);
            jdbc.update("UPDATE plan_versions SET body=?::jsonb WHERE version=?",mapper.writeValueAsString(body),version);
        } else if(option!=null && !option.isBlank()) throw new IllegalArgumentException("No shortfall option required");
        return transition(version,Status.approved,approvedBy);
    }

    public int afterPlan(LocalDate date,String lotId,java.time.LocalTime time,int forecast) {
        var active=applied(date);
        if(active.isEmpty()) return forecast;
        var body=active.get().body();
        var from=java.time.LocalTime.parse(body.path("window").path("from").asString());
        var to=java.time.LocalTime.parse(body.path("window").path("to").asString());
        if(time.isBefore(from) || time.isAfter(to)) return forecast;
        long count=forecast;
        for(var move:body.path("moves")) {
            if(lotId.equals(move.path("from").asString())) count-=move.path("vehicles").asInt();
            if(lotId.equals(move.path("to").asString())) count+=move.path("vehicles").asInt();
        }
        return (int)Math.max(0,count);
    }
    @Transactional
    public Version publish(LocalDate date,ObjectNode body,PlanHysteresis.Snapshot snapshot) {
        lock(date);
        var previous=latest(date);
        var candidate=create(date,body,snapshot);
        if(previous.isPresent() && previous.get().version()==candidate.version()) return candidate;
        var active=applied(date);
        boolean automatic=active.isPresent() && withinEnvelope(body,active.get().body().path("envelope"));
        if(automatic) {
            body=body.deepCopy();
            body.set("envelope",active.get().body().path("envelope").deepCopy());
            jdbc.update("UPDATE plan_versions SET body=?::jsonb WHERE version=?",mapper.writeValueAsString(body),candidate.version());
        }
        return transition(candidate.version(),automatic?Status.auto_applied:Status.pending_approval,automatic?"system":null);
    }

    private boolean withinEnvelope(ObjectNode body,tools.jackson.databind.JsonNode envelope) {
        if(body.path("shortfall").asInt(-1)!=0 || !envelope.isObject()) return false;
        long total=0;
        java.util.Set<String> allowed=new java.util.HashSet<>();
        envelope.path("allowedDest").forEach(n->allowed.add(n.asString()));
        for(var move:body.path("moves")) {
            total+=move.path("vehicles").asInt();
            if(!allowed.contains(move.path("to").asString())) return false;
        }
        if(total>envelope.path("maxMoveTotal").asInt(0)) return false;
        for(var lot:body.path("after")) {
            // Chỉ áp giới hạn đích lên các bãi nhận xe, không lên bãi nguồn quá tải.
            boolean destination=false;
            for(var move:body.path("moves")) if(move.path("to").asString().equals(lot.path("lotId").asString())) destination=true;
            if(destination && lot.path("pct").asDouble()>envelope.path("maxTargetPct").asDouble(0)*100) return false;
        }
        return true;
    }
}