package org.drupal.project.computing;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Database record for this Command.
 * This is the "boundary object" between DCommand and DApplication
 */
public class DRecord implements Comparable<DRecord> {

    /**
     * Status of the record, usually set by the program. See computing.module too.
     */
    public static enum Status {
        OKOK, // successful
        FAIL, // failed (expected)
        RUNG, // running
        STOP, // forced stop
        NRCG, // not recognized
        INTR, // internal error, unexpected program error.
    }

    /**
     * Control how to execute the record, usually set by Drupal. See computing.module too.
     */
    public static enum Control {
        REDY, // ready for pull execution.
        CNCL, // cancel
        REMT, // remote
        CMLN, // record is created from command line push running mode.
        CODE, // record is created from code, and pushed to computing_record.
        //AAAA, // top priority
    }

    // this variables can't change after construction.
    /*private final Long id;
    private final String app;
    private final String command;
    private final String description;
    private final Long uid; // nullable, can't change during runtime.
    private final Long nid; // nullable, can't change during runtime.
    private final Long created;*/

    // remove the final decorator to facilitate json creation.
    private Long id;
    private String app;
    private String command;
    private String description;
    private Long uid; // nullable, can't change during runtime.
    private Long nid; // nullable, can't change during runtime.
    private Long created;

    // these are input/output parameters
    private byte[] input;
    private byte[] output;
    private String inputjson;   // use inputjson rather than inputJson in order to use toJson().
    private String outputjson;  // similar reason
    private Long id1;
    private Long id2;
    private Long id3;
    private Long id4;
    private String ids1;
    private String ids2;
    private String ids3;
    private String ids4;
    private Float number1;
    private Float number2;
    private Float number3;
    private Float number4;
    private String string1;
    private String string2;
    private String string3;
    private String string4;

    // these are status/control parameters.
    private String status;   // internally saved as String, but setter/getter are Status enum.
    private String control;  // internally saved as String, but setter/getter are Control enum.
    private String message;
    private String agent;
    private Long weight;
    private Long start;
    private Long end;
    private Long updated;
    private Float progress;

    /**
     * Points to the site where this record belongs to. Can't get changed after construction.
     * If needs to change, create a new record instead.
     */
    // protected final DSite site;


    /**
     * Given the database query result, construct a Record object.
     * This is easier for callers to pass in parameters, regardless of parameters position or numbers.
     *
     * @param map Database map for this record, should exact match the record.
     */
    public DRecord(Map<String, Object> map) {
        assert map != null;

        // don't need to do assertion here. if map doesn't contain the key, the value is just null.
        //assert map.containsKey("id") && map.containsKey("app") && map.containsKey("command") && map.containsKey("description")
        //        && map.containsKey("uid") && map.containsKey("nid") && map.containsKey("created") && map.containsKey("input")
        //        && map.containsKey("output") && map.containsKey("id1") && map.containsKey("id2") && map.containsKey("number1")
        //        && map.containsKey("number2") && map.containsKey("string1") && map.containsKey("string2") && map.containsKey("status")
        //        && map.containsKey("control") && map.containsKey("message") && map.containsKey("dependency") && map.containsKey("start")
        //        && map.containsKey("end") && map.containsKey("checkpoint") && map.containsKey("progress");


        this.id = DUtils.getInstance().getLong(map.get("id")); // if id is null, it means this is a dummy record.
        this.app = (String) map.get("app");     // could be null for dummys, assert app != null;
        this.command = (String) map.get("command");    // could be null for dummys, assert command != null;
        this.description = (String) map.get("description");   // note: even though this can be null, class type cast would still work.
        this.uid = DUtils.getInstance().getLong(map.get("uid"));
        this.nid = DUtils.getInstance().getLong(map.get("nid"));
        this.created = DUtils.getInstance().getLong(map.get("created"));

        this.input = (byte[]) map.get("input");
        this.output = (byte[]) map.get("output");
        this.inputjson = (String) map.get("inputjson");
        this.outputjson = (String) map.get("outputjson");
        this.id1 = DUtils.getInstance().getLong(map.get("id1"));
        this.id2 = DUtils.getInstance().getLong(map.get("id2"));
        this.id3 = DUtils.getInstance().getLong(map.get("id3"));
        this.id4 = DUtils.getInstance().getLong(map.get("id4"));
        this.ids1 = (String) map.get("ids1");
        this.ids2 = (String) map.get("ids2");
        this.ids3 = (String) map.get("ids3");
        this.ids4 = (String) map.get("ids4");
        this.number1 = (Float) map.get("number1");
        this.number2 = (Float) map.get("number2");
        this.number3 = (Float) map.get("number3");
        this.number4 = (Float) map.get("number4");
        this.string1 = (String) map.get("string1");
        this.string2 = (String) map.get("string2");
        this.string3 = (String) map.get("string3");
        this.string4 = (String) map.get("string4");

        this.status = (String) map.get("status");
        this.control = (String) map.get("control");
        this.message = (String) map.get("message");
        this.agent = (String) map.get("agent");
        this.weight = DUtils.getInstance().getLong(map.get("weight"));   // could be null for dummies, assert weight != null;
        this.start = DUtils.getInstance().getLong(map.get("start"));
        this.end = DUtils.getInstance().getLong(map.get("end"));
        this.updated = DUtils.getInstance().getLong(map.get("checkpoint"));
        this.progress = (Float) map.get("progress");

        // post check string length. note that they could be null
        assert (status == null || status.length() == 4) && (control == null || control.length() == 4)
                && (app == null || app.length() <= 50) && (command == null || command.length() <= 50
                && (agent == null || agent.length() <= 20));
        // we don't check other length, which might get truncated.
    }

    public static DRecord create(String app) {
        return create(app, null, null, null, null, null);
    }

    public static DRecord create(String app, String command, String description, Long uid, Long nid, Long created) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("app", app);
        map.put("command", command);
        map.put("description", description);
        map.put("uid", uid);
        map.put("nid", nid);
        map.put("created", created);
        return new DRecord(map);
    }

    /**
     * The smaller the weight, created, or id, the smaller the record. Smaller record would get executed first.
     *
     * @param otherRecord
     * @return
     */
    @Override
    public int compareTo(DRecord otherRecord) {
        // first, compare weight
        int compareWeight = this.weight.compareTo(otherRecord.weight);
        if (compareWeight != 0) {
            return compareWeight;
        }
        // then compare created
        int compareCreated = this.created.compareTo(otherRecord.created);
        if (compareCreated != 0) {
            return compareCreated;
        }
        // finally compare id.
        return this.id.compareTo(otherRecord.id);
    }

    @Override
    public String toString() {
        Properties properties = toProperties();
        return properties.toString();
    }

    public String toJson() {
        return DUtils.getInstance().getDefaultGson().toJson(this);
    }

    public Properties toProperties() {
        Gson gson = DUtils.getInstance().getDefaultGson();
        String json = gson.toJson(this);
        return gson.fromJson(json, Properties.class);
    }

    /**
     * Return a map of the record.
     * This can be done with Apache BeanUtils, but this is preferred for more control.
     * Just to get a list of stuff could use this.toProperties() too.
     *
     * @return
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("id", id);
        map.put("app", app);
        map.put("command", command);
        map.put("description", description);
        map.put("uid", uid);
        map.put("nid", nid);
        map.put("created", created);

        map.put("input", input);
        map.put("output", output);
        map.put("inputjson", inputjson);
        map.put("outputjson", outputjson);
        map.put("id1", id1);
        map.put("id2", id2);
        map.put("id3", id3);
        map.put("id4", id4);
        map.put("ids1", ids1);
        map.put("ids2", ids2);
        map.put("ids3", ids3);
        map.put("ids4", ids4);
        map.put("string1", string1);
        map.put("string2", string2);
        map.put("string3", string3);
        map.put("string4", string4);
        map.put("number1", number1);
        map.put("number2", number2);
        map.put("number3", number3);
        map.put("number4", number4);

        map.put("status", status);      // note: here is the string, not enum
        map.put("control", control);    // note: here is the string, not enum
        map.put("message", message);
        map.put("weight", weight);
        map.put("agent", agent);
        map.put("start", start);
        map.put("end", end);
        map.put("updated", updated);
        map.put("progress", progress);

        return map;
    }

    public String readInput() {
        return input == null ? null : new String(input);
    }

    public void writeOutput(String output) {
        this.output = output == null ? null : output.getBytes();
    }


    public boolean isSaved() {
        return id != null;
    }

    /**
     * A record is active when "status" is not set. When status is set, the record is not active anymore
     * @return
     */
    public boolean isActive() {
        return status == null;
    }

    /**
     * A record is ready when it is active as well as "control" is set to be READY.
     * @return
     */
    public boolean isReady() {
        return isActive() && getControl() == Control.REDY;
    }


    ////////////////////// getters and setters /////////////////////////

    public byte[] getInput() {
        return input;
    }

    public void setInput(byte[] input) {
        this.input = input;
    }

    public byte[] getOutput() {
        return output;
    }

    public void setOutput(byte[] output) {
        this.output = output;
    }

    public Long getId1() {
        return id1;
    }

    public void setId1(Long id1) {
        this.id1 = id1;
    }

    public Long getId2() {
        return id2;
    }

    public void setId2(Long id2) {
        this.id2 = id2;
    }

    public Long getId3() {
        return id3;
    }

    public void setId3(Long id3) {
        this.id3 = id3;
    }

    public Long getId4() {
        return id4;
    }

    public void setId4(Long id4) {
        this.id4 = id4;
    }

    public String getIds1() {
        return ids1;
    }

    public void setIds1(String ids1) {
        this.ids1 = ids1;
    }

    public String getIds2() {
        return ids2;
    }

    public void setIds2(String ids2) {
        this.ids2 = ids2;
    }

    public String getIds3() {
        return ids3;
    }

    public void setIds3(String ids3) {
        this.ids3 = ids3;
    }

    public String getIds4() {
        return ids4;
    }

    public void setIds4(String ids4) {
        this.ids4 = ids4;
    }

    public Float getNumber1() {
        return number1;
    }

    public void setNumber1(Float number1) {
        this.number1 = number1;
    }

    public Float getNumber2() {
        return number2;
    }

    public void setNumber2(Float number2) {
        this.number2 = number2;
    }

    public Float getNumber3() {
        return number3;
    }

    public void setNumber3(Float number3) {
        this.number3 = number3;
    }

    public Float getNumber4() {
        return number4;
    }

    public void setNumber4(Float number4) {
        this.number4 = number4;
    }

    public String getString1() {
        return string1;
    }

    public void setString1(String string1) {
        this.string1 = string1;
    }

    public String getString2() {
        return string2;
    }

    public void setString2(String string2) {
        this.string2 = string2;
    }

    public String getString3() {
        return string3;
    }

    public void setString3(String string3) {
        this.string3 = string3;
    }

    public String getString4() {
        return string4;
    }

    public void setString4(String string4) {
        this.string4 = string4;
    }

    public Status getStatus() {
        return status == null ? null : Status.valueOf(status);
    }

    public void setStatus(Status status) {
        assert status != null;
        this.status = status.toString();
        assert this.status.length() == 4;
    }

    public Control getControl() {
        return control == null ? null : Control.valueOf(control);
    }

    public void setControl(Control control) {
        assert control != null;
        this.control = control.toString();
        assert this.control.length() == 4;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getWeight() {
        return weight;
    }

    public void setWeight(Long weight) {
        this.weight = weight;
    }

    public Long getStart() {
        return start;
    }

    public void setStart(Long start) {
        this.start = start;
    }

    public Long getEnd() {
        return end;
    }

    public void setEnd(Long end) {
        this.end = end;
    }

    public Long getUpdated() {
        return updated;
    }

    public void setUpdated(Long updated) {
        this.updated = updated;
    }

    public Float getProgress() {
        return progress;
    }

    public void setProgress(Float progress) {
        this.progress = progress;
    }


    public Long getId() {
        return id;
    }

    public String getApp() {
        return app;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        // this is to assert that command doesn't get changed in runtime.
        // it could be null and get set later.
        assert command != null && this.command == null;
        this.command = command;
    }

    public String getDescription() {
        return description;
    }

    public Long getUid() {
        return uid;
    }

    public Long getNid() {
        return nid;
    }

    public void setUid(Long uid) {
        // this is to assert that uid doesn't get changed in runtime.
        // it could be null and get set later.
        assert uid != null && this.uid == null;
        this.uid = uid;
    }

    public void setNid(Long nid) {
        assert nid != null && this.nid == null;
        this.nid = nid;
    }

    public Long getCreated() {
        return created;
    }

    public String getInputjson() {
        return inputjson;
    }

    public void setInputjson(String inputjson) {
        this.inputjson = inputjson;
    }

    public String getOutputjson() {
        return outputjson;
    }

    public void setOutputjson(String outputjson) {
        this.outputjson = outputjson;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCreated(Long created) {
        assert created != null && this.created == null;
        this.created = created;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }
}
