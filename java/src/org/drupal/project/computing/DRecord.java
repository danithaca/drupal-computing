package org.drupal.project.computing;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;

import javax.script.Bindings;
import javax.script.SimpleBindings;

/**
 * This class maps to a Computing Record entity in Drupal. It serves as the "boundary object" between Drupal and Agent.
 * Input/Output/etc only encodes in Json string before getting initialized into DRecord object. All DRecord object are
 * ready-to-process with Json encoding etc taken care of. See Drupal Computing documentation for more details about the
 * Computing Record entity.
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


    /**
     * Default constructor. Does nothing.
     */
    public DRecord() {}

    /**
     * Constructor that takes four important parameters.
     * @param application the Application name of this record, eg "computing".
     * @param command the Command name of this record, eg "echo".
     * @param label the human readable label of the record, eg "Echo Message"
     * @param input the input to this record.
     */
    public DRecord(String application, String command, String label, Bindings input) {
        this();
        assert StringUtils.isNotBlank(application) && StringUtils.isNotBlank(command) && StringUtils.isNotBlank(label);

        this.setApplication(application);
        this.setCommand(command);
        this.setLabel(label);

        if (input != null) {
            this.setInput(input);
        }
    }

    /**
     * Factory method. Create a DRecord object from JSON string.
     * Required field in JSON: id, application, command
     * Expected Input/Output not as strings (enclosed by "") but as original json encodings (enclosed by {})
     *
     * @param jsonString the JSON string to create the DRecord object.
     *      We expect it to be a valid DRecord encoded JSON string. If not, it will throw an exception.
     * @return the DRecord object.
     */
    public static DRecord fromJson(String jsonString) throws JsonParseException, JsonSyntaxException, IllegalArgumentException {
        Bindings jsonObj;
        try {
            jsonObj = DUtils.Json.getInstance().fromJsonObject(jsonString);
        } catch (ClassCastException e) {
            throw new JsonParseException("Cannot parse JSON correctly for DRecord", e);
        }
        return fromBindings(jsonObj);
    }


    /**
     * Constructor from Bindings object.
     *
     * @param jsonObj the Bindings object
     * @return a DRecord object from the Bindings object.
     */
    public static DRecord fromBindings(Bindings jsonObj) throws JsonParseException, JsonSyntaxException, IllegalArgumentException {
        DRecord record = new DRecord();
        DUtils utils = DUtils.getInstance();

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
        if (jsonObj.containsKey("message")) record.setMessage((String) jsonObj.get("message"));
        if (jsonObj.containsKey("uid")) record.setUid(utils.getLong(jsonObj.get("uid")));
        if (jsonObj.containsKey("created")) record.setCreated(utils.getLong(jsonObj.get("created")));
        if (jsonObj.containsKey("changed")) record.setChanged(utils.getLong(jsonObj.get("changed")));
        if (jsonObj.containsKey("weight")) record.setWeight(utils.getLong(jsonObj.get("weight")));

        if (jsonObj.containsKey("status")) {
            // this setter handles String object too.
            record.setStatus((String) jsonObj.get("status"));
        }

        // Note that we assume Input/Output are JSON Object (ie Bindings), not primitives (ie Integer, String, etc).
        // To use primitives, extend this class.

        try {
            if (jsonObj.containsKey("input")) {
                record.setInput((Bindings) jsonObj.get("input"));
            }
            if (jsonObj.containsKey("output")) {
                record.setOutput((Bindings) jsonObj.get("output"));
            }
        } catch (ClassCastException | JsonParseException e) {
            throw new JsonParseException("Cannot parse JSON correctly for DRecord", e);
        }

        return record;
    }


    /**
     * Encode the object into Bindings object.
     * ATTENTION: input/output are still in Bindings. Not encoded in String. They should be encoded as String in toJson().
     *
     * @return a Bindings object from the record.
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
     * ATTENTION: we don't encode Input/Output as String before encode the entire object
     *
     * @return the encoded json string.
     */
    public String toJson() {
        Bindings jsonObj = toBindings();
        return DUtils.Json.getInstance().toJson(jsonObj);
    }


    /**
     * If ID is not set yet, that means the record is pragmatically created and not persisted yet.
     * @return true if the record is created within agent program, instead of loaded from Drupal.
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
