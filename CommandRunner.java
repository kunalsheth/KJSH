package com.the_magical_llamicorn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystemAlreadyExistsException;
import java.util.LinkedList;

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
  * This code is meant to manage generating Java source code from user
  * input, storing the source code files, compiling the code, loading the
  * classes described by the source code, and calling the run() method
  * of the loaded KJSH command class
  */

public class CommandRunner {

    private static final LinkedList<String[]> previousKjshCommands = new LinkedList<>();

    private static final String CLASS_NAME_PREFIX = "KJSH_command_";
    private static final String[] CLASS_TEMPLATE = new String[]{"\n/** This file's copyright is held by the user that created it */\n/* This code was generate by KJSH */\n\npublic class ", " extends ", " implements Runnable {public void run(){try{\n", "\n}catch(Throwable t){t.printStackTrace();}}\n", "\n}"};
    private static final String[] ROOT_CLASS_IMPORTS_LINES_CLASS_COMPONENTS = new String[]{"import java.io.IOException;", "System.out.println(\"Welcome to Kunal's Java Shell (KJSH)\\nVersion: \" + KJSH.getVersion());", "public void help() {System.out.println(\"I'm too lazy to type out a man page. Sorry.\\nhttps://github.com/the-magical-llamicorn/KJSH\");}public static class KJSH {public static final String VERSION = \"1.2 alpha\";public static String getVersion() {return VERSION;}public static void clear() {for (int i = 0; i < 100; i++) System.out.println();}public static int runInShell(String command) throws IOException, InterruptedException {if (command == null || (command = command.trim()).equals(\"\")) return 0;if (command.contains(\";\")) {int exitCode = 0, returnCode = 0;String[] subCommands = command.split(\";\");for (int i = 0; i < subCommands.length; i++) if ((exitCode = runInShell(subCommands[i])) < returnCode) returnCode = exitCode;return returnCode;}ProcessBuilder processBuilder = new ProcessBuilder().inheritIO();processBuilder.command(command.split(\" \"));return processBuilder.start().waitFor();}}"};

    private static final ProcessBuilder COMPILER_PROCESS_BUILDER = new ProcessBuilder().inheritIO();
    private static final File SOURCE_DIRECTORY;

    private static int classId = -1;

    static {
        String[] args = Main.getArgs();

        File customSourceDirectory = null;
        int resumeClassId = 0;
        if (args.length == 2 && args[0] != null && args[0].equalsIgnoreCase("--srcDir")) {
            customSourceDirectory = new File(args[1]);
            if (!customSourceDirectory.exists() && !customSourceDirectory.mkdirs())
                throw new ExceptionInInitializerError(new IOException("!SOURCE_DIRECTORY.exists() && !SOURCE_DIRECTORY.mkdirs()"));
            String[] list = customSourceDirectory.list((dir, name) -> name.endsWith(".java"));

            if (list != null) {
                resumeClassId = list.length - 1;
                Main.setPersist(true);
            } else resumeClassId = classId;
        }

        SOURCE_DIRECTORY = customSourceDirectory == null ? new File(System.getProperty("java.io.tmpdir")+File.separator+"kjsh_" + System.currentTimeMillis()) : customSourceDirectory;
        if (!SOURCE_DIRECTORY.exists() && !SOURCE_DIRECTORY.mkdirs())
            throw new ExceptionInInitializerError(new IOException("(!SOURCE_DIRECTORY.exists() && !SOURCE_DIRECTORY.mkdirs()) is true"));

        runKjshInput(ROOT_CLASS_IMPORTS_LINES_CLASS_COMPONENTS[0], ROOT_CLASS_IMPORTS_LINES_CLASS_COMPONENTS[1], ROOT_CLASS_IMPORTS_LINES_CLASS_COMPONENTS[2]);
        classId = resumeClassId;
    }

    protected static void start() {
    }

    protected static void reset() {
        classId = -1;

        File[] files = SOURCE_DIRECTORY.listFiles();
        if (files == null)
            throw new ExceptionInInitializerError(new IOException("SOURCE_DIRECTORY.listFiles() is null"));
        for (int i = 0; i < files.length; i++) files[i].delete();

        runKjshInput(ROOT_CLASS_IMPORTS_LINES_CLASS_COMPONENTS[0], ROOT_CLASS_IMPORTS_LINES_CLASS_COMPONENTS[1], ROOT_CLASS_IMPORTS_LINES_CLASS_COMPONENTS[2]);
    }

    protected static File getSourceDirectory() {
        return SOURCE_DIRECTORY;
    }

    protected static void runKjshInput(String imports, String lines, String classComponents) {
        String[] classAndParentClassNames = getClassAndParentClassName();

        previousKjshCommands.push(new String[]{imports, classAndParentClassNames[1], classAndParentClassNames[0], lines, classComponents});

        String sourceCode = buildSourceFromKJSHInput(imports, classAndParentClassNames[1], classAndParentClassNames[0], lines, classComponents);

        File file = null;
        try {
            file = writeSourceToFile(classAndParentClassNames[1], sourceCode);
            ((Runnable) compileAndLoadSourceCode(classAndParentClassNames[1]).newInstance()).run();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | IOException | InterruptedException e) {
            e.printStackTrace();

            if (file != null) file.delete();
            classId--;
        }
    }

    private static String[] getClassAndParentClassName() {
        return new String[]{(classId == -1 ? "Object" : CLASS_NAME_PREFIX + classId), CLASS_NAME_PREFIX + ++classId};
    }

    private static File writeSourceToFile(String className, String sourceCode) throws IOException {
        File file = new File(SOURCE_DIRECTORY.getAbsolutePath() + File.separator + className + ".java");
        if (file.exists() && !file.delete()) throw new FileSystemAlreadyExistsException(file.getAbsolutePath());
        OutputStream outputStream = new FileOutputStream(file);
        outputStream.write(sourceCode.getBytes());
        outputStream.flush();
        outputStream.close();
        return file;
    }

    private static Class<?> compileAndLoadSourceCode(String className) throws ClassNotFoundException, IOException, InterruptedException {
        if (className == null || SOURCE_DIRECTORY == null) return null;

        File[] files = SOURCE_DIRECTORY.listFiles(pathname -> pathname.getAbsolutePath().endsWith(".java"));

        if (files == null) return null;

        String[] command = new String[files.length + 1];
        command[0] = "javac";
        for (int i = 0; i < files.length; i++) {
            command[i + 1] = files[i].getAbsolutePath();
        }

        COMPILER_PROCESS_BUILDER.command(command);
        Process process = COMPILER_PROCESS_BUILDER.start();
        process.waitFor();

        ClassLoader loader = new URLClassLoader(new URL[]{new File(SOURCE_DIRECTORY.getAbsolutePath()).toURI().toURL()});
        return loader.loadClass(className);
    }

    private static String buildSourceFromKJSHInput(String imports, String className, String parentClass, String lines, String classComponents) {
        return imports + CLASS_TEMPLATE[0] + className + CLASS_TEMPLATE[1] + parentClass + CLASS_TEMPLATE[2] + lines + CLASS_TEMPLATE[3] + classComponents + CLASS_TEMPLATE[4];
    }

    public static int getClassId() {
        return classId;
    }
}
