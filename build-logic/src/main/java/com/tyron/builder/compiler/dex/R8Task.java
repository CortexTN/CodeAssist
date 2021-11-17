package com.tyron.builder.compiler.dex;

import android.util.Log;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.origin.Origin;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.compiler.Task;
import com.tyron.builder.exception.CompilationFailedException;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.model.Project;
import com.tyron.builder.parser.FileManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class R8Task extends Task {

    private static final String TAG = R8Task.class.getSimpleName();

    private Project mProject;
    private ILogger mLogger;

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public void prepare(Project project, ILogger logger, BuildType type) throws IOException {
        mProject = project;
        mLogger = logger;
    }

    @Override
    public void run() throws IOException, CompilationFailedException {
        mLogger.debug("Running R8");
        try {
            File output = new File(mProject.getBuildDirectory(), "bin");
            R8Command.Builder command = R8Command.builder(new DiagnosticHandler())
                    .addLibraryFiles(getLibraryFiles())
                    .addProgramFiles(getJarFiles())
                    .addProgramFiles(D8Task.getClassFiles(new File(mProject.getBuildDirectory(),
                            "bin/classes")))
                    .addProguardConfiguration(getDefaultProguardRule(), Origin.unknown())
                    .addProguardConfigurationFiles(getProguardRules())
                    .setMinApiLevel(mProject.getMinSdk())
                    .setMode(CompilationMode.RELEASE)
                    .setOutput(output.toPath(), OutputMode.DexIndexed);

            R8.run(command.build());
        } catch (com.android.tools.r8.CompilationFailedException e) {
            throw new CompilationFailedException(e);
        }
    }

    private List<String> getDefaultProguardRule() {
        List<String> rules = new ArrayList<>();
        // Keep resource classes
        rules.addAll(createConfiguration("-keepclassmembers class **.R$* {\n" +
                "    public static <fields>;\n" +
                "}"));
        rules.addAll(createConfiguration("-keepclassmembers class *" +
                " implements android.os.Parcelable {\n" +
                "  public static final android.os.Parcelable$Creator CREATOR;\n" +
                "}"));
        // For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
        rules.addAll(createConfiguration("-keepclasseswithmembernames class * {\n" +
                "    native <methods>;\n" +
                "}"));
        rules.addAll(createConfiguration("-keepclassmembers enum * {\n" +
                "    public static **[] values();\n" +
                "    public static ** valueOf(java.lang.String);\n" +
                "}"));
        return rules;
    }

    private List<String> createConfiguration(String string) {
        return Arrays.asList(string.split("\n"));
    }

    private Collection<Path> getJarFiles() {
        Collection<Path> paths = new ArrayList<>();
        for (File it : mProject.getLibraries()) {
            paths.add(it.toPath());
        }
        return paths;
    }

    private List<Path> getProguardRules() {
        List<Path> rules = new ArrayList<>();
        mProject.getLibraries().forEach(it -> {
            File parent = it.getParentFile();
            if (parent != null) {
                File proguardFile = new File(parent, "proguard.txt");
                if (proguardFile.exists()) {
                    rules.add(proguardFile.toPath());
                }
            }
        });

        File proguardRuleTxt = new File(mProject.mRoot, "app/proguard-rules.txt");
        if (proguardRuleTxt.exists()) {
            rules.add(proguardRuleTxt.toPath());
        }

        File aapt2Rules = new File(mProject.getBuildDirectory(),
                "bin/res/generated-rules.txt");
        if (aapt2Rules.exists()) {
            rules.add(aapt2Rules.toPath());
        }
        return rules;
    }

    private List<Path> getLibraryFiles() {
        List<Path> path = new ArrayList<>();
        path.add(FileManager.getAndroidJar().toPath());
        path.add(FileManager.getLambdaStubs().toPath());
        return path;
    }

    public class DiagnosticHandler implements DiagnosticsHandler {
        @Override
        public void error(Diagnostic diagnostic) {
            mLogger.error(wrap(diagnostic));
        }

        @Override
        public void warning(Diagnostic diagnostic) {
            mLogger.warning(wrap(diagnostic));
        }

        @Override
        public void info(Diagnostic diagnostic) {
            mLogger.info(wrap(diagnostic));
        }

        @Override
        public DiagnosticsLevel modifyDiagnosticsLevel(DiagnosticsLevel diagnosticsLevel,
                                                       Diagnostic diagnostic) {
            Log.d("DiagnosticHandler", diagnostic.getDiagnosticMessage());
            return null;
        }

        private DiagnosticWrapper wrap(Diagnostic diagnostic) {
            DiagnosticWrapper wrapper = new DiagnosticWrapper();
            wrapper.setMessage(diagnostic.getDiagnosticMessage());
            return wrapper;
        }
    }
}