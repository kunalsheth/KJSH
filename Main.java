package com.the_magical_llamicorn;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystemAlreadyExistsException;

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
 * This code is meant to treat Java in such a way, as to replace BASH and other linux shells
 * I recommend that you read my source code in order to learn how to use this shell
 */

public class Main {

    private static final String LINE_HEADER = "\nkjsh$ ";

    private static final BufferedReader BUFFERED_READER = new BufferedReader(new InputStreamReader(System.in));

    private static final int LINES = 0;
    private static final int CLASS = 1;
    private static final int IMPORTS = 2;
    private static final String CLASS_NAME = "KJSH_command_";
    private static final String[] CLASS_TEMPLATE = new String[]{"\n/** This file's copyright is held by the user that created it */\n/* This code was generate by KJSH */\npublic class ", " extends ", " implements Runnable {public void run(){try{\n", "\n}catch(Throwable t){t.printStackTrace();}}\n", "\n}"};
    private static final String ROOT_CLASS;
    private static final ProcessBuilder COMPILER_PROCESS_BUILDER = new ProcessBuilder().inheritIO();

    private static File srcDir = new File("/tmp/kjsh_" + System.currentTimeMillis() + "/");
    private static int addToLocation = 0;
    private static String imports = "";
    private static String lines = "";
    private static String classComponents = "";
    private static int tabs = 0;
    private static int classId = 0;
    private static boolean waitUntilDone = false;

    static {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream("KJSH_root_command.src")));
            StringBuilder fileData = new StringBuilder();
            String ln;
            while ((ln = bufferedReader.readLine()) != null) {
                fileData.append(ln);
                fileData.append('\n');
            }
            ROOT_CLASS = fileData.toString();
        } catch (IOException e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (args.length == 2 && args[0] != null && args[0].equalsIgnoreCase("--srcDir")) {
            srcDir = new File(args[1]);
        }

        if (!srcDir.exists() && !srcDir.mkdirs())
            throw new ExceptionInInitializerError(new IOException("!srcDir.exists() && !srcDir.mkdirs()"));
        else {
            String[] list = srcDir.list((dir, name) -> name.endsWith(".java"));
            classId = (list == null ? 0 : list.length);
        }

        Thread rootClassThread = createCommandThread(CLASS_NAME + 0, ROOT_CLASS);
        rootClassThread.start();
        while (!rootClassThread.getState().equals(Thread.State.TERMINATED)) {
            Thread.sleep(1);
        }

        while (true) {
            System.out.print(LINE_HEADER);
            for (int i = 0; i < tabs; i++) System.out.print('\t');

            String ln = BUFFERED_READER.readLine();
            if (ln == null) System.exit(0);
            else if (ln.trim().equals("")) continue;

            String annotation = parseAnnotation(ln);

            if (annotation == null) {
                if (addToLocation == IMPORTS) imports += ln + '\n';
                else if (addToLocation == LINES) lines += ln + '\n';
                else if (addToLocation == CLASS) classComponents += ln + '\n';

                tabs = 0;
                tabs += occurrencesOfChar('{', lines);
                tabs -= occurrencesOfChar('}', lines);
                tabs += occurrencesOfChar('{', classComponents);
                tabs -= occurrencesOfChar('}', classComponents);

                if (!waitUntilDone) annotation = "done";
                else continue;
            }

            if (annotation.equalsIgnoreCase("done")) {
                String source = buildSource(imports, CLASS_NAME + ++classId, CLASS_NAME + (classId - 1), lines, classComponents);
                lines = "";
                classComponents = "";
                Thread command = createCommandThread(CLASS_NAME + classId, source);
                command.start();
            } else if (annotation.equalsIgnoreCase("nowait")) waitUntilDone = false;
            else if (annotation.equalsIgnoreCase("wait")) waitUntilDone = true;

            else if (annotation.equalsIgnoreCase("import")) addToLocation = IMPORTS;
            else if (annotation.equalsIgnoreCase("class")) addToLocation = CLASS;
            else if (annotation.equalsIgnoreCase("lines")) addToLocation = LINES;
        }
    }

    private static Thread createCommandThread(String className, String javaSource) throws IOException {
        File file = new File(srcDir.getAbsolutePath() + File.separator + className + ".java");
        if (file.exists() && !file.delete()) throw new FileSystemAlreadyExistsException(file.getAbsolutePath());

        OutputStream outputStream = new FileOutputStream(file);
        outputStream.write(javaSource.getBytes());
        outputStream.flush();
        outputStream.close();

        try {
            Thread thread = new Thread((Runnable) compile(className, srcDir).newInstance());
            thread.setName(className + " Executor");
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
            return thread;
        } catch (Throwable t) {
            classId--;
            return new Thread(new Runnable() {
                public void run() {
                    t.printStackTrace();
                }
            });
        }
    }

    private static Class<?> compile(String className, File sourceDir) throws ClassNotFoundException, IOException, InterruptedException {
        if (className == null || sourceDir == null) return null;

        File[] files = sourceDir.listFiles(pathname -> pathname.getAbsolutePath().endsWith(".java"));

        if (files == null) return null;

        String[] command = new String[files.length + 1];
        command[0] = "javac";
        for (int i = 0; i < files.length; i++) {
            command[i + 1] = files[i].getAbsolutePath();
        }

        COMPILER_PROCESS_BUILDER.command(command);
        Process process = COMPILER_PROCESS_BUILDER.start();
        process.waitFor();

        ClassLoader loader = new URLClassLoader(new URL[]{new File(sourceDir.getAbsolutePath()).toURI().toURL()});
        return loader.loadClass(className);
    }

    private static String buildSource(String imports, String className, String parentClass, String lines, String classComponents) {
        return imports + CLASS_TEMPLATE[0] + className + CLASS_TEMPLATE[1] + parentClass + CLASS_TEMPLATE[2] + lines + CLASS_TEMPLATE[3] + classComponents + CLASS_TEMPLATE[4];
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
}

// Copyright 2016 Kunal Sheth
