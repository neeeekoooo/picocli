/*
   Copyright 2017 Remko Popma

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package picocli;

import org.junit.Test;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.String.*;
import static org.junit.Assert.*;

/**
 * Tests the scripts generated by AutoComplete.
 */
// http://hayne.net/MacDev/Notes/unixFAQ.html#shellStartup
// https://apple.stackexchange.com/a/13019
public class AutoCompleteTest {
    public static void main(String[] args) {
        TopLevel.main(args);
    }
    public static class BasicExample implements Runnable {
        @Option(names = {"-u", "--timeUnit"}) private TimeUnit timeUnit;
        @Option(names = {"-t", "--timeout"}) private long timeout;
        public void run() {
            System.out.printf("BasicExample was invoked with %d %s.%n", timeout, timeUnit);
        }
        public static void main(String[] args) { CommandLine.run(new BasicExample(), System.out, args); }
    }
    @Test
    public void basic() throws Exception {
        String script = AutoComplete.bash("basicExample", new CommandLine(new BasicExample()));
        String expected = format(loadTextFromClasspath("/basic.bash"),
                CommandLine.VERSION, spaced(TimeUnit.values()));
        assertEquals(expected, script);
    }

    public static class TopLevel {
        @Option(names = {"-V", "--version"}, help = true) boolean versionRequested;
        @Option(names = {"-h", "--help"}, help = true) boolean helpRequested;
        public static void main(String[] args) {
            CommandLine hierarchy = new CommandLine(new TopLevel())
                    .addSubcommand("sub1", new Sub1())
                    .addSubcommand("sub2", new CommandLine(new Sub2())
                            .addSubcommand("subsub1", new Sub2Child1())
                            .addSubcommand("subsub2", new Sub2Child2())
                    );
            List<CommandLine> commandLines = hierarchy.parse(args);
            //Collections.reverse(commandLines);
            for (CommandLine cmdLine : commandLines) {
                Object command = cmdLine.getCommand();
                System.out.printf("Parsed command %s%n", AutoCompleteTest.toString(command));
            }
        }
    }
    @Command(description = "First level subcommand 1")
    public static class Sub1 {
        @Option(names = "--num", description = "a number") double number;
        @Option(names = "--str", description = "a String") String str;
    }
    @Command(description = "First level subcommand 2")
    public static class Sub2 {
        @Option(names = "--num2", description = "another number") int number2;
        @Option(names = {"--directory", "-d"}, description = "a directory") File directory;
    }
    @Command(description = "Second level sub-subcommand 1")
    public static class Sub2Child1 {
        @Option(names = {"-h", "--host"}, description = "a host") InetAddress host;
    }
    @Command(description = "Second level sub-subcommand 2")
    public static class Sub2Child2 {
        @Option(names = {"-u", "--timeUnit"}) private TimeUnit timeUnit;
        @Option(names = {"-t", "--timeout"}) private long timeout;
    }

    @Test
    public void nestedSubcommands() throws Exception {
        CommandLine hierarchy = new CommandLine(new TopLevel())
                .addSubcommand("sub1", new Sub1())
                .addSubcommand("sub2", new CommandLine(new Sub2())
                        .addSubcommand("subsub1", new Sub2Child1())
                        .addSubcommand("subsub2", new Sub2Child2())
                );
        String script = AutoComplete.bash("picocompletion-demo", hierarchy);
        String expected = format(loadTextFromClasspath("/picocompletion-demo_completion"),
                CommandLine.VERSION, spaced(TimeUnit.values()));
        assertEquals(expected, script);
    }

    private static String spaced(Object[] values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            result.append(value).append(' ');
        }
        return result.toString().substring(0, result.length() - 1);
    }

    private static String loadTextFromClasspath(String path) {
        URL url = AutoCompleteTest.class.getResource(path);
        if (url == null) { throw new IllegalArgumentException("Could not find '" + path + "' in classpath."); }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuilder result = new StringBuilder(512);
            char[] buff = new char[4096];
            int read = 0;
            do {
                result.append(buff, 0, read);
                read = reader.read(buff);
            } while (read >= 0);
            return result.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + url + " for '" + path + "':", ex);
        } finally {
            if (reader != null) { try { reader.close(); } catch (IOException e) { /* ignore */ } }
        }
    }

    private static String toString(Object obj) {
        StringBuilder sb = new StringBuilder(256);
        Class<?> cls = obj.getClass();
        sb.append(cls.getSimpleName()).append("[");
        String sep = "";
        for (Field f : cls.getDeclaredFields()) {
            f.setAccessible(true);
            sb.append(sep).append(f.getName()).append("=");
            try { sb.append(f.get(obj)); } catch (Exception ex) { sb.append(ex); }
            sep = ", ";
        }
        return sb.append("]").toString();
    }

    private static final String AUTO_COMPLETE_APP_USAGE = String.format("" +
            "Usage: picocli.AutoComplete [-fhw] [-n=<commandName>] [-o=<autoCompleteScript>]%n" +
            "                            <commandLineFQCN>%n" +
            "Generates a bash completion script for the specified command class.%n" +
            "      <commandLineFQCN>       Fully qualified class name of the annotated%n" +
            "                                @Command class to generate a completion script%n" +
            "                                for.%n" +
            "  -n, --name=<commandName>    Name of the command to create a completion script%n" +
            "                                for. When omitted, the annotated class @Command%n" +
            "                                'name' attribute is used. If no @Command 'name'%n" +
            "                                attribute exists, '<CLASS-SIMPLE-NAME>' (in%n" +
            "                                lower-case) is used.%n" +
            "  -o, --completionScript=<autoCompleteScript>%n" +
            "                              Path of the completion script file to generate.%n" +
            "                                When omitted, a file named%n" +
            "                                '<commandName>_completion' is generated in the%n" +
            "                                current directory.%n" +
            "  -w, --writeCommandScript    Write a '<commandName>' sample command script to%n" +
            "                                the same directory as the completion script.%n" +
            "  -f, --force                 Overwrite existing script files.%n" +
            "  -h, --help                  Display this help message and quit.%n");

    @Test
    public void testAutoCompleteAppHelp() throws Exception {
        String[][] argsList = new String[][] {
                {"-h"},
                {"--help"},
        };
        for (String[] args : argsList) {
            PrintStream originalErr = System.err;
            ByteArrayOutputStream baos = new ByteArrayOutputStream(2500);
            System.setErr(new PrintStream(baos));

            AutoComplete.main(args);

            System.setErr(originalErr);
            String actual = new String(baos.toByteArray(), "UTF8");
            assertEquals(AUTO_COMPLETE_APP_USAGE, actual);
        }
    }

    @Test
    public void testAutoCompleteRequiresCommandLineFQCN() throws Exception {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2500);
        System.setErr(new PrintStream(baos));

        AutoComplete.main();

        System.setErr(originalErr);
        String actual = new String(baos.toByteArray(), "UTF8");
        String expected = String.format("Missing required parameter: <commandLineFQCN>%n") + AUTO_COMPLETE_APP_USAGE;
        assertEquals(expected, actual);
    }

    @Test
    public void testAutoCompleteAppCannotInstantiate() throws Exception {
        @Command(name = "test")
        class TestApp {
            public TestApp() { throw new RuntimeException();}
        }

        PrintStream originalErr = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2500);
        System.setErr(new PrintStream(baos));

        AutoComplete.main(TestApp.class.getName());

        System.setErr(originalErr);
        String actual = new String(baos.toByteArray(), "UTF8");
        assertTrue(actual.startsWith("java.lang.InstantiationException: picocli.AutoCompleteTest$1TestApp"));
        assertTrue(actual.endsWith(AUTO_COMPLETE_APP_USAGE));
    }

    @Test
    public void testAutoCompleteAppCompletionScriptFileWillNotOverwrite() throws Exception {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2500);
        System.setErr(new PrintStream(baos));

        File dir = new File(System.getProperty("java.io.tmpdir"));
        File completionScript = new File(dir, "App_completion");
        if (completionScript.exists()) {assertTrue(completionScript.delete());}
        completionScript.deleteOnExit();

        // create the file
        FileOutputStream fous = new FileOutputStream(completionScript, false);
        fous.close();

        AutoComplete.main(String.format("-o=%s", completionScript.getAbsolutePath()), "picocli.AutoComplete$App");

        System.setErr(originalErr);
        String actual = new String(baos.toByteArray(), "UTF8");
        //System.out.println(actual);

        String expected = String.format("" +
                "%s exists. Specify -f to overwrite.%n" +
                "%s", completionScript.getAbsolutePath(), AUTO_COMPLETE_APP_USAGE);
        assertEquals(expected, actual);
    }

    @Test
    public void testAutoCompleteAppCommandScriptFileWillNotOverwrite() throws Exception {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2500);
        System.setErr(new PrintStream(baos));

        File dir = new File(System.getProperty("java.io.tmpdir"));
        File commandScript = new File(dir, "picocli.AutoComplete");
        if (commandScript.exists()) {assertTrue(commandScript.delete());}
        commandScript.deleteOnExit();

        // create the file
        FileOutputStream fous = new FileOutputStream(commandScript, false);
        fous.close();

        File completionScript = new File(dir, commandScript.getName() + "_completion");
        AutoComplete.main("--writeCommandScript", String.format("-o=%s", completionScript.getAbsolutePath()), "picocli.AutoComplete$App");

        System.setErr(originalErr);
        String actual = new String(baos.toByteArray(), "UTF8");
        //System.out.println(actual);

        String expected = String.format("" +
                "%s exists. Specify -f to overwrite.%n" +
                "%s", commandScript.getAbsolutePath(), AUTO_COMPLETE_APP_USAGE);
        assertEquals(expected, actual);
    }

    @Test
    public void testAutoCompleteAppBothScriptFilesForceOverwrite() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        File commandScript = new File(dir, "picocli.AutoComplete");
        if (commandScript.exists()) {assertTrue(commandScript.delete());}
        commandScript.deleteOnExit();

        // create the file
        FileOutputStream fous1 = new FileOutputStream(commandScript, false);
        fous1.close();

        File completionScript = new File(dir, commandScript.getName() + "_completion");
        if (completionScript.exists()) {assertTrue(completionScript.delete());}
        completionScript.deleteOnExit();

        // create the file
        FileOutputStream fous2 = new FileOutputStream(completionScript, false);
        fous2.close();

        AutoComplete.main("--force", "--writeCommandScript", String.format("-o=%s", completionScript.getAbsolutePath()), "picocli.AutoComplete$App");

        byte[] command = readBytes(commandScript);
        assertEquals(("" +
                "#!/usr/bin/env bash\n" +
                "\n" +
                "LIBS=path/to/libs\n" +
                "CP=\"${LIBS}/myApp.jar\"\n" +
                "java -cp \"${CP}\" 'picocli.AutoComplete$App' $@"), new String(command, "UTF8"));

        byte[] completion = readBytes(completionScript);

        String expected = ("" +
                "#!/usr/bin/env bash\n" +
                "#\n" +
                "# picocli.AutoComplete Bash Completion\n" +
                "# =======================\n" +
                "#\n" +
                "# Bash completion support for the `picocli.AutoComplete` command,\n" +
                "# generated by [picocli](http://picocli.info/) version 2.2.0.\n" +
                "#\n" +
                "# Installation\n" +
                "# ------------\n" +
                "#\n" +
                "# 1. Place this file in a `bash-completion.d` folder:\n" +
                "#\n" +
                "#   * /etc/bash-completion.d\n" +
                "#   * /usr/local/etc/bash-completion.d\n" +
                "#   * ~/bash-completion.d\n" +
                "#\n" +
                "# 2. Open a new bash console, and type `picocli.AutoComplete [TAB][TAB]`\n" +
                "#\n" +
                "# Documentation\n" +
                "# -------------\n" +
                "# The script is called by bash whenever [TAB] or [TAB][TAB] is pressed after\n" +
                "# 'picocli.AutoComplete (..)'. By reading entered command line parameters,\n" +
                "# it determines possible bash completions and writes them to the COMPREPLY variable.\n" +
                "# Bash then completes the user input if only one entry is listed in the variable or\n" +
                "# shows the options if more than one is listed in COMPREPLY.\n" +
                "#\n" +
                "# References\n" +
                "# ----------\n" +
                "# [1] http://stackoverflow.com/a/12495480/1440785\n" +
                "# [2] http://tiswww.case.edu/php/chet/bash/FAQ\n" +
                "# [3] https://www.gnu.org/software/bash/manual/html_node/The-Shopt-Builtin.html\n" +
                "# [4] https://stackoverflow.com/questions/17042057/bash-check-element-in-array-for-elements-in-another-array/17042655#17042655\n" +
                "# [5] https://www.gnu.org/software/bash/manual/html_node/Programmable-Completion.html#Programmable-Completion\n" +
                "#\n" +
                "\n" +
                "# Enable programmable completion facilities (see [3])\n" +
                "shopt -s progcomp\n" +
                "\n" +
                "# ArrContains takes two arguments, both of which are the name of arrays.\n" +
                "# It creates a temporary hash from lArr1 and then checks if all elements of lArr2\n" +
                "# are in the hashtable.\n" +
                "#\n" +
                "# Returns zero (no error) if all elements of the 2nd array are in the 1st array,\n" +
                "# otherwise returns 1 (error).\n" +
                "#\n" +
                "# Modified from [4]\n" +
                "function ArrContains() {\n" +
                "  local lArr1 lArr2\n" +
                "  declare -A tmp\n" +
                "  eval lArr1=(\"\\\"\\${$1[@]}\\\"\")\n" +
                "  eval lArr2=(\"\\\"\\${$2[@]}\\\"\")\n" +
                "  for i in \"${lArr1[@]}\";{ [ -n \"$i\" ] && ((++tmp[$i]));}\n" +
                "  for i in \"${lArr2[@]}\";{ [ -n \"$i\" ] && [ -z \"${tmp[$i]}\" ] && return 1;}\n" +
                "  return 0\n" +
                "}\n" +
                "\n" +
                "# Bash completion entry point function.\n" +
                "# _complete_picocli.AutoComplete finds which commands and subcommands have been specified\n" +
                "# on the command line and delegates to the appropriate function\n" +
                "# to generate possible options and subcommands for the last specified subcommand.\n" +
                "function _complete_picocli.AutoComplete() {\n" +
                "\n" +
                "\n" +
                "  # No subcommands were specified; generate completions for the top-level command.\n" +
                "  _picocli_picocli.AutoComplete; return $?;\n" +
                "}\n" +
                "\n" +
                "# Generates completions for the options and subcommands of the `picocli.AutoComplete` command.\n" +
                "function _picocli_picocli.AutoComplete() {\n" +
                "  # Get completion data\n" +
                "  CURR_WORD=${COMP_WORDS[COMP_CWORD]}\n" +
                "  PREV_WORD=${COMP_WORDS[COMP_CWORD-1]}\n" +
                "\n" +
                "  COMMANDS=\"\"\n" +
                "  FLAG_OPTS=\"-w --writeCommandScript -f --force -h --help\"\n" +
                "  ARG_OPTS=\"-n --name -o --completionScript\"\n" +
                "\n" +
                "  case ${CURR_WORD} in\n" +
                "    -o|--completionScript)\n" +
                "      compopt -o filenames\n" +
                "      COMPREPLY=( $( compgen -f -- \"\" ) ) # files\n" +
                "      return $?\n" +
                "      ;;\n" +
                "    *)\n" +
                "      case ${PREV_WORD} in\n" +
                "        -o|--completionScript)\n" +
                "          compopt -o filenames\n" +
                "          COMPREPLY=( $( compgen -f -- $CURR_WORD ) ) # files\n" +
                "          return $?\n" +
                "          ;;\n" +
                "      esac\n" +
                "  esac\n" +
                "\n" +
                "  COMPREPLY=( $(compgen -W \"${FLAG_OPTS} ${ARG_OPTS} ${COMMANDS}\" -- ${CURR_WORD}) )\n" +
                "}\n" +
                "\n" +
                "# Define a completion specification (a compspec) for the\n" +
                "# `picocli.AutoComplete`, `picocli.AutoComplete.sh`, and `picocli.AutoComplete.bash` commands.\n" +
                "# Uses the bash `complete` builtin (see [5]) to specify that shell function\n" +
                "# `_complete_picocli.AutoComplete` is responsible for generating possible completions for the\n" +
                "# current word on the command line.\n" +
                "# The `-o default` option means that if the function generated no matches, the\n" +
                "# default Bash completions and the Readline default filename completions are performed.\n" +
                "complete -F _complete_picocli.AutoComplete -o default picocli.AutoComplete picocli.AutoComplete.sh picocli.AutoComplete.bash\n");
        assertEquals(expected, new String(completion, "UTF8"));
    }
    private byte[] readBytes(File f) throws IOException {
        int pos = 0;
        int len = 0;
        byte[] buffer = new byte[(int) f.length()];
        FileInputStream fis = new FileInputStream(f);
        while ((len = fis.read(buffer, pos, buffer.length - pos)) > 0) { pos += len; }
        fis.close();
        return buffer;
    }
    @Test
    public void testCommandDescriptor() {
        AutoComplete.CommandDescriptor descriptor = new AutoComplete.CommandDescriptor("aaa", "bbb");
        assertEquals(descriptor, descriptor);

        AutoComplete.CommandDescriptor other = new AutoComplete.CommandDescriptor("111", "222");
        assertNotEquals(descriptor, other);

        assertEquals(descriptor.hashCode(), descriptor.hashCode());
        assertEquals(other.hashCode(), other.hashCode());
        assertNotEquals(other.hashCode(), descriptor.hashCode());
    }


    @Test
    public void testBashRejectsNullScript() {
        try {
            AutoComplete.bash(null, new CommandLine(new TopLevel()));
            fail("Expected NPE");
        } catch (NullPointerException ok) {
            assertEquals("scriptName", ok.getMessage());
        }
    }

    @Test
    public void testBashRejectsNullCommandLine() {
        try {
            AutoComplete.bash("script", null);
            fail("Expected NPE");
        } catch (NullPointerException ok) {
            assertEquals("commandLine", ok.getMessage());
        }
    }

    @Test
    public void testBashAcceptsNullCommand() throws Exception {
        File temp = File.createTempFile("abc", "b");
        temp.deleteOnExit();
        AutoComplete.bash("script", temp, null, new CommandLine(new TopLevel()));
        assertTrue(temp.length() > 0);
    }

    @Test
    public void testBashRejectsNullOut() throws Exception {
        File commandFile = File.createTempFile("abc", "b");
        commandFile.deleteOnExit();
        try {
            AutoComplete.bash("script", null, commandFile,  new CommandLine(new TopLevel()));
            fail("Expected NPE");
        } catch (NullPointerException ok) {
            assertEquals(null, ok.getMessage());
        }
    }
}
