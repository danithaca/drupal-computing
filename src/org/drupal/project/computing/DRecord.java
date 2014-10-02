package org.drupal.project.computing;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Maps to a Computing entity in Drupal.
 * Serves as the "boundary object" between Drupal and Agent.
 *
 * Input/Output/etc only encodes in Json string before getting initialized into DRecord object.
 * All DRecord object are ready-to-process with Json encoding etc taken care of.
 *
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
    private String application; // this can't map to an application before execution.
    private String command;
    private String label;
    private Long uid; // nullable, can't change during runtime.
    private Long created;
    // these are input/output and status parameters
    private Bindings input;
    private Bindings output;
    private Status status;
    private String message;
    private Long weight;
    private Long changed;


    public DRecord() {
        // default constructor.
    }

    /**
     * Factory method. Create a DRecord object from JSON string.
     * Required field in JSON: id, application, command
     *
     * @param jsonString the JSON string to create the DRecord object.
     * @return the DRecord object.
     */
    public static DRecord fromJson(String jsonString) throws JsonSyntaxException, JsonParseException, IllegalArgumentException {
        DRecord record = new DRecord();
        DUtils utils = DUtils.getInstance();
        Bindings jsonObj;

        try {
            jsonObj = (Bindings) DUtils.Json.getInstance().fromJson(jsonString);
        } catch (ClassCastException e) {
            throw new JsonParseException("Cannot parse JSON correctly for DRecord", e);
        }

        // set ID, required
        if (jsonObj.containsKey("id")) {
            record.setId(utils.getLong(jsonObj.get("id")));
        } else throw new IllegalArgumentException("Cannot retrieve ID field from JSON");

        // set Application, required
        if (jsonObj.containsKey("application")) {
            record.setApplication((String) jsonObj.get("application"));
        } else throw new IllegalArgumentException("Cannot retrieve Application field from JSON");

        // set Command, required
        if (jsonObj.containsKey("command")) {
            record.setCommand((String) jsonObj.get("command"));
        } else throw new IllegalArgumentException("Cannot retrieve Command field from JSON");

        // set other optional parameters
        if (jsonObj.containsKey("label")) record.setLabel((String) jsonObj.get("label"));
        if (jsonObj.containsKey("message")) record.setLabel((String) jsonObj.get("message"));
        if (jsonObj.containsKey("uid")) record.setUid(utils.getLong(jsonObj.get("uid")));
        if (jsonObj.containsKey("created")) record.setUid(utils.getLong(jsonObj.get("created")));
        if (jsonObj.containsKey("changed")) record.setUid(utils.getLong(jsonObj.get("changed")));
        if (jsonObj.containsKey("weight")) record.setUid(utils.getLong(jsonObj.get("weight")));

        if (jsonObj.containsKey("status")) {
            // this setter handles String object too.
            record.setStatus((String) jsonObj.get("status"));
        }

        // handle input/output. DUtils.Json.fromJson() should have already parsed JsonString into JsonElement.
        // Note that we assume Input/Output are JSON Object, not primitives. To use primitives, extend this class.
        if (jsonObj.containsKey("input")) {
            record.setInput((Bindings) jsonObj.get("input"));
        }
        if (jsonObj.containsKey("output")) {
            record.setInput((Bindings) jsonObj.get("output"));
        }

        return record;
    }

    /**
     * Encode the object into Bindings object.
     *
     * @return
     */
    public Bindings toBindings() {
        Bindings jsonObj = new SimpleBindings();

        if (getId() != null) jsonObj.put("id", getId());
        if (getApplication() != null) jsonObj.put("application", getApplication());
        if (getCommand() != null) jsonObj.put("command", getCommand());
        if (getLabel() != null) jsonObj.put("label", getLabel());
        if (getMessage() != null) jsonObj.put("message", getMessage());
        if (getUid() != null) jsonObj.put("uid", getUid());
        if (getCreated() != null) jsonObj.put("created", getCreated());
        if (getChanged() != null) jsonObj.put("changed", getChanged());
        if (getWeight() != null) jsonObj.put("weight", getWeight());
        if (getStatus() != null) jsonObj.put("status", getStatus().toString());
        if (getInput() != null) jsonObj.put("input", getInput());
        if (getOutput() != null) jsonObj.put("output", getOutput());

        return jsonObj;
    }

    /**
     * Encode the DRecord in JSON string. We don't validate the object here. Validation is on the receiving end.
     *
     * @return the encoded json string.
     */
    public String toJson() {
        Bindings jsonObj = toBindings();
        return DUtils.Json.getInstance().toJson(jsonObj);
    }


    /**
     * If ID is not set yet, that means the record is pragmatically created and not persisted yet.
     * @return
     */
    public boolean isNew() {
        return getId() == null;
    }


    //////////////////////////// getters and setters ///////////////////////////

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        // do not change "id" of the record if it's already set.
        assert id >= 0 && this.id == null;
        this.id = id;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        // do not change "application" of the record.
        assert application != null && this.application == null;
        this.application = application;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        // do not change "command" of the record.
        assert command != null && this.command == null;
        this.command = command;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public Bindings getInput() {
        return input;
    }

    public void setInput(Bindings input) {
        this.input = input;
    }

    public Bindings getOutput() {
        return output;
    }

    public void setOutput(Bindings output) {
        this.output = output;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setStatus(String statusCode) {
        this.status = (statusCode == null) ? null : Status.valueOf(statusCode);
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

    public Long getChanged() {
        return changed;
    }

    public void setChanged(Long changed) {
        this.changed = changed;
    }
}
