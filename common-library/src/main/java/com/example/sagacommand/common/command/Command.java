package com.example.sagacommand.common.command;

public interface Command<T> {
    T execute();

    void undo();
}
