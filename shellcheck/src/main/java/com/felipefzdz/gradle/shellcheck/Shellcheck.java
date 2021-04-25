package com.felipefzdz.gradle.shellcheck;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.util.ClosureBackedAction;

import javax.inject.Inject;
import java.io.File;

@CacheableTask
abstract public class Shellcheck extends ConventionTask implements VerificationTask, Reporting<ShellcheckReports> {

    private ShellcheckReports reports;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract public ConfigurableFileCollection getSources();

    /**
     * Configure whether violations are shown on the console.
     * @return the flag value
     */
    @Console
    abstract public Property<Boolean> getShowViolations();

    /**
     * Configure whether shellcheck is run via Docker container or local command line.
     * @return the flag value, true == docker
     */
    @Input
//    @Optional
    abstract public Property<Boolean> getUseDocker();
    @Input
//    @Optional
    abstract public Property<Boolean> getContinueBuildOnFailure();
    @Input
//    @Optional
    abstract public Property<String> getShellcheckImage();
    @Input
//    @Optional
    abstract public Property<String> getShellcheckVersion();
    @Input
//    @Optional
    abstract public Property<String> getShellcheckBinary();
    @Input
//    @Optional
    abstract public Property<String> getSeverity();
    @Internal
    abstract public RegularFileProperty getProjectDir();

    @Inject
    public Shellcheck(ObjectFactory objects) {
        this.reports = (ShellcheckReports) objects.newInstance(ShellcheckReportsImpl.class, this);
    }

    @TaskAction
    public void run() {
        ShellcheckInvoker.invoke(this);
    }

    @Override
    public boolean getIgnoreFailures() {
        return getContinueBuildOnFailure().get();
    }

    @Override
    public void setIgnoreFailures(boolean flag) {
        getContinueBuildOnFailure().set(flag);
    }

    /**
     * The reports to be generated by this task.
     */
    @Override
    @Nested
    public final ShellcheckReports getReports() {
        return reports;
    }

    /**
     * Configures the reports to be generated by this task.
     * <p>
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * shellcheck {
     *   reports {
     *     html {
     *       destination "build/shellcheck.html"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The configuration
     * @return The reports container
     */
    @Override
    public ShellcheckReports reports(@DelegatesTo(value = ShellcheckReports.class, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        return reports(new ClosureBackedAction<>(closure));
    }

    /**
     * Configures the reports to be generated by this task.
     * <p>
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * shellcheck {
     *   reports {
     *     html {
     *       destination "build/shellcheck.html"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param configureAction The configuration
     * @return The reports container
     */
    @Override
    public ShellcheckReports reports(Action<? super ShellcheckReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

}
