package org.drupal.project.computing;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Maps to a Computing entity in Drupal.
 * Services the "boundary object" between Drupal and Agent.
 */
public class DRecord {

    /**
     * Status of the record, usually set by the program. See computing.module too.
     */
    public static enum Status {
        RDY, // Ready
        SCF, // successful
        FLD, // failed (expected)
        RUN, // running
        ABD, // Aborted, or Abandoned.
    }

    // this variables can't change after construction.
    // remove the final decorator to facilitate json creation.
    private Long id;
    private String application;
    private String command;
    private String label;
    private Long uid; // nullable, can't change during runtime.
    private Long created;

    // these are input/output and status parameters
    private byte[] input;
    private byte[] output;
    private String status;   // internally saved as String, but setter/getter are Status enum.
    private String message;
    private Long weight;
    private Long changed;

    /**
     * Points to the site where this record belongs to. Can't get changed after construction.
     * If needs to change, create a new record instead.
     */
    // protected final DSite site;


    /**
     * From Drupal json output, construct a Record object.
     * This is easier for callers to pass in parameters, regardless of parameters position or numbers.
     *
     * @param map Database map for this record, should exact match the record.
     */
    public DRecord(Map<String, Object> map) {
        assert map != null;

        this.id = DUtils.getInstance().getLong(map.get("id")); // if id is null, it means this is a dummy record.
        this.application = (String) map.get("application");     // could be null for dummys, assert application != null;
        this.command = (String) map.get("command");    // could be null for dummys, assert command != null;
        this.label = (String) map.get("label");   // note: even though this can be null, class type cast would still work.
        this.uid = DUtils.getInstance().getLong(map.get("uid"));
        this.created = DUtils.getInstance().getLong(map.get("created"));

        this.input = (byte[]) map.get("input");
        this.output = (byte[]) map.get("output");

        this.status = (String) map.get("status");
        this.message = (String) map.get("message");
        this.weight = DUtils.getInstance().getLong(map.get("weight"));   // could be null for dummies, assert weight != null;
        this.changed = DUtils.getInstance().getLong(map.get("changed"));

        // post check string length. note that they could be null
        assert (status == null || status.length() == 3) && (application == null || application.length() <= 50) && (command == null || command.length() <= 50);
        // we don't check other length, which might get truncated.
    }


    public static DRecord create(String application, String command, String label, Map<String, Object> input) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("application", application);
        map.put("command", command);
        map.put("label", label);
        return new DRecord(map);
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
        map.put("application", application);
        map.put("command", command);
        map.put("label", label);
        map.put("uid", uid);
        map.put("created", created);

        map.put("input", input);
        map.put("output", output);

        map.put("status", status);      // note: here is the string, not enum
        map.put("message", message);
        map.put("weight", weight);
        map.put("changed", changed);

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

    public Status getStatus() {
        return status == null ? null : Status.valueOf(status);
    }

    public void setStatus(Status status) {
        assert status != null;
        this.status = status.toString();
        assert this.status.length() == 4;
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



    public Long getId() {
        return id;
    }

    public String getApplication() {
        return application;
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

    public String getLabel() {
        return label;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        // this is to assert that uid doesn't get changed in runtime.
        // it could be null and get set later.
        assert uid != null && this.uid == null;
        this.uid = uid;
    }

    public Long getCreated() {
        return created;
    }


    public void setLabel(String label) {
        this.label = label;
    }

    public void setCreated(Long created) {
        assert created != null && this.created == null;
        this.created = created;
    }
}
