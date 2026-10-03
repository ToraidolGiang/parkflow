package com.parkflow.backend.sim;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import com.parkflow.backend.common.ParkFlowClock;
import org.springframework.core.io.ClassPathResource;

public final class ReplayLog {
    private ReplayLog() {}
    public record Event(String id,Instant ts,String lot,String direction,String vehicle,String card,Instant exit) {
        public Map<String,Object> payload() { return Map.of("event_id",id,"ts",ts.atZone(ParkFlowClock.ZONE).toOffsetDateTime().toString(),
                "lot_id",lot,"direction",direction,"vehicle_type",vehicle,"card_hash",card); }
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static String id(String ts,String lot,String card,String direction) {
        return "evt_"+hash(ts+"|"+lot+"|"+card+"|"+direction).substring(0,40);
    }
    /** Ngày có log demo riêng; các ngày lịch sử được đọc từ swipe_log.csv. */
    public static final LocalDate DEMO_DATE=LocalDate.of(2026,10,3);
    public static List<Event> read(LocalDate date) {
        String file=date.equals(DEMO_DATE)?"demo_log.csv":"swipe_log.csv";
        return read(file,date);
    }
    public static List<Event> history() { return read("swipe_log.csv",null); }
    public static List<LocalDate> dates() {
        var dates = new TreeSet<LocalDate>();
        history().forEach(event -> dates.add(event.ts().atZone(ParkFlowClock.ZONE).toLocalDate()));
        if (!read(DEMO_DATE).isEmpty()) dates.add(DEMO_DATE);
        return List.copyOf(dates);
    }
    private static List<Event> read(String file,LocalDate date) {
        List<Event> result=new ArrayList<>();
        try(var reader=new BufferedReader(new InputStreamReader(new ClassPathResource("simulator/"+file).getInputStream(),StandardCharsets.UTF_8))) {
            if(!"ts_in,ts_out,gate_id,card_id,lot_id,vehicle_type".equals(reader.readLine())) throw new IllegalArgumentException("Invalid log header");
            for(String line;(line=reader.readLine())!=null;) {
                String[] r=line.split(",",-1);if(r.length!=6) throw new IllegalArgumentException("Invalid row");
                String vehicle=switch(r[5]) {case "Xe máy"->"motorbike";case "Xe đạp"->"bicycle";case "Ô tô"->"car";default->throw new IllegalArgumentException("Invalid vehicle");};
                Instant exit=r[1].isBlank()?null:parse(r[1]);
                for(int i=0;i<2;i++) if(!r[i].isBlank() && (date==null || r[i].startsWith(date+" "))) {
                    String direction=i==0?"in":"out";
                    result.add(new Event(id(r[i],r[4],r[3],direction),parse(r[i]),r[4],direction,vehicle,r[3],i==0?exit:null));
                }
            }
        } catch(IOException e) { throw new IllegalStateException("Cannot load packaged simulation log",e); }
        result.sort(Comparator.comparing(Event::ts).thenComparing(Event::id));return result;
    }
    private static Instant parse(String text) { return LocalDateTime.parse(text.replace(' ','T')).atZone(ParkFlowClock.ZONE).toInstant(); }
}
