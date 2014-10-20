package org.drupal.project.computing.v1.common;

import org.apache.commons.lang3.StringUtils;
import org.drupal.project.computing.v1.DCommand;
import org.drupal.project.computing.v1.DCommandExecutionException;
import org.drupal.project.computing.v1.DRecord;
import org.drupal.project.computing.v1.Identifier;

/**
 * This is a simple command that might be used in any DApplication.
 */
@Identifier("PingMe")
public class PingMe extends DCommand {

    private String message;

    private void initialize(String message) {
        this.message = message;
    }

    @Override
    public void enterRecord(DRecord record) {
        initialize(record.getString1());
    }

    @Override
    public void mapArgs(DRecord record, String[] args) {
        record.setString1(StringUtils.join(args, " "));
    }

    @Override
    public void mapParams(DRecord record, Object... params) {
        String[] args = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = params[i].toString();
        }
        mapArgs(record, args);
    }

    @Override
    public void keepRecord(DRecord record) {
        record.setString2(message);
    }


    @Override
    protected void execute() throws DCommandExecutionException {
        if (StringUtils.isEmpty(message)) {
            printMessage("Pong.");
        } else {
            printMessage("Pong with message: " + message);
        }
    }
}
