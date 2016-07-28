package com.the_magical_llamicorn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Copyright 2016 Kunal Sheth
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This code is meant to manage user I/O and forward that data to the
 * CommandRunner class
 */

public class Main {

    private static final String LINE_HEADER = "kjsh$";

    private static final BufferedReader BUFFERED_READER = new BufferedReader(new InputStreamReader(System.in));
    private static final int ADD_TO_LINES = 0;
    private static final int ADD_TO_CLASS = 1;
    private static final int ADD_TO_IMPORTS = 2;

    private static String[] args;
    private static int addToBuffer = 0;
    private static String imports = "";
    private static String lines = "";
    private static String classComponents = "";
    private static boolean waitUntilDone = false;

    private static boolean persist = false;

    public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        Main.args = args;

        CommandRunner.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println(persist ? "Source directory saved at " + CommandRunner.getSourceDirectory() : "Deleting source directory");
                if (!persist) {
                    ProcessBuilder processBuilder = new ProcessBuilder().inheritIO();
                    processBuilder.command("rm", "-r", CommandRunner.getSourceDirectory().getAbsolutePath());
                    try {
                        processBuilder.start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        while (true) {
            StringBuilder header = new StringBuilder();
            header.append("\n");
            header.append(CommandRunner.getClassId() + 1);
            header.append(" (");
            header.append(waitUntilDone ? "wait" : "nowait");
            header.append("|");
            if (addToBuffer == ADD_TO_IMPORTS) header.append("imports");
            else if (addToBuffer == ADD_TO_LINES) header.append("lines");
            else if (addToBuffer == ADD_TO_CLASS) header.append("class");
            header.append(") ");
            header.append(LINE_HEADER);
            header.append(' ');
            int tabs = 0;
            tabs += occurrencesOfChar('{', lines);
            tabs -= occurrencesOfChar('}', lines);
            tabs += occurrencesOfChar('{', classComponents);
            tabs -= occurrencesOfChar('}', classComponents);
            for (int i = 0; i < tabs; i++) header.append('\t');
            System.out.print(header);

            String ln = BUFFERED_READER.readLine();
            if (ln == null) System.exit(0);
            else if (ln.trim().equals("")) continue;

            String annotation = parseAnnotation(ln);

            if (annotation == null) {
                if (addToBuffer == ADD_TO_IMPORTS) imports += ln + '\n';
                else if (addToBuffer == ADD_TO_LINES) lines += ln + '\n';
                else if (addToBuffer == ADD_TO_CLASS) classComponents += ln + '\n';

                if (!waitUntilDone) annotation = "Run";
                else continue;
            }

            if (annotation.equalsIgnoreCase("Run")) {
                CommandRunner.runKjshInput(imports, lines, classComponents);
                lines = "";
                classComponents = "";
            } else if (annotation.equalsIgnoreCase("Wait")) waitUntilDone = true;
            else if (annotation.equalsIgnoreCase("NoWait")) waitUntilDone = false;

            else if (annotation.equalsIgnoreCase("Imports")) addToBuffer = ADD_TO_IMPORTS;
            else if (annotation.equalsIgnoreCase("Class")) addToBuffer = ADD_TO_CLASS;
            else if (annotation.equalsIgnoreCase("Lines")) addToBuffer = ADD_TO_LINES;

            else if (annotation.equalsIgnoreCase("Imports?")) System.out.println(imports);
            else if (annotation.equalsIgnoreCase("Lines?")) System.out.println(lines);
            else if (annotation.equalsIgnoreCase("Class?")) System.out.println(classComponents);

            else if (annotation.equalsIgnoreCase("ClearImports")) imports = "";
            else if (annotation.equalsIgnoreCase("ClearLines")) lines = "";
            else if (annotation.equalsIgnoreCase("ClearClass")) classComponents = "";

            else if (annotation.equalsIgnoreCase("NoPersist")) persist = false;
            else if (annotation.equalsIgnoreCase("Persist")) persist = true;
            else if (annotation.equalsIgnoreCase("Persist?")) System.out.println(persist);

            else if (annotation.equalsIgnoreCase("ResetSrcDir")) {
                CommandRunner.reset();
                lines = "";
                classComponents = "";
                imports = "";
            } else {
                System.out.println("@What?");
            }
        }
    }

    private static String parseAnnotation(String line) {
        if (line == null || (line = line.trim()).charAt(0) != '@') return null;
        return line.substring(1).trim();
    }

    private static int occurrencesOfChar(char character, String text) {
        int occurrences = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == character) occurrences++;
        return occurrences;
    }

    protected static String[] getArgs() {
        return args.clone();
    }

    public static boolean isPersist() {
        return persist;
    }

    public static void setPersist(boolean persist) {
        Main.persist = persist;
    }
}
