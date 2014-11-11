package org.drupal.project.computing.common;

import org.drupal.project.computing.DCommand;
import org.drupal.project.computing.exception.DCommandExecutionException;

import javax.script.Bindings;

/**
 * A simple DCommand implementation that echos the "input" string into "output".
 */
public class EchoCommand extends DCommand {

    String pingString;

    @Override
    public void prepare(Bindings input) throws IllegalArgumentException {
        try {
            pingString  = (String) input.get("ping");
        } catch (NullPointerException | ClassCastException e) {
            throw new IllegalArgumentException("Cannot get input parameter, the 'ping' string.");
        }
    }

    @Override
    public void execute() throws DCommandExecutionException {
        result.put("pong", (pingString == null) ? "N/A" : pingString);
        message.append("Echo successful.");
    }

}
