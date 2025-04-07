package com.example.sagacommand.common.command;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;

@Slf4j
public class ScopedCommandInvoker implements AutoCloseable {

    private final CommandInvoker commandInvoker;

    public ScopedCommandInvoker() {
        this.commandInvoker = new CommandInvoker();
    }

    public <T> T executeCommand(Command<T> command) {
        return commandInvoker.executeCommand(command);
    }

    public void undoLastCommand() {
        commandInvoker.undoLastCommand();
    }

    public void undoAllCommands() {
        commandInvoker.undoAllCommands();
    }

    public void clear() {
        commandInvoker.clear();
    }

    @Override
    public void close() {
        clear();
    }

    private static class CommandInvoker {

        private final ThreadLocal<Deque<Command<?>>> commandHistoryHolder =
                ThreadLocal.withInitial(ArrayDeque::new);

        private CommandInvoker() {
        }

        public <T> T executeCommand(Command<T> command) {
            T result = command.execute();
            commandHistoryHolder.get().push(command);
            return result;
        }

        public void undoLastCommand() {
            Deque<Command<?>> history = commandHistoryHolder.get();

            if (!history.isEmpty()) {
                Command<?> command = history.pop();
                try {
                    command.undo();
                } catch (Exception e) {
                    log.warn("Undo failed for command {}: {}", command.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }

        public void undoAllCommands() {
            Deque<Command<?>> history = commandHistoryHolder.get();

            while (!history.isEmpty()) {
                Command<?> command = history.pop();
                try {
                    command.undo();
                } catch (Exception e) {
                    log.warn("Undo failed for command {}: {}", command.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }

        public void clear() {
            commandHistoryHolder.remove();
        }
    }
}
