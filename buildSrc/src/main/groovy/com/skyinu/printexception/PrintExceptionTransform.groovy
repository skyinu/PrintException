package com.skyinu.printexception

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.api.transform.Format
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.google.common.collect.ImmutableSet
import org.apache.commons.io.FileUtils
import org.gradle.api.Project;


public class PrintExceptionTransform extends Transform {
    private Project project;
    private AssistHandler assistHandler;

    public PrintExceptionTransform(Project project) {
        this.project = project
        this.assistHandler = new AssistHandler(project)
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)
        assistHandler.insertClassPath(project.android.bootClasspath[0].toString())
        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        transformInvocation.inputs.each {
            it.jarInputs.each {
                File out = outputProvider.getContentLocation(it.name, it.contentTypes, it.scopes,
                        Format.JAR)
                project.logger.error("input jar = " + it.file.path)
                FileUtils.copyFile(it.file, out)
                project.logger.error("add classPath = " + out.path)
                assistHandler.insertClassPath(out.path)
            }
            handleDirectoryInputs(outputProvider, it.directoryInputs)
        }
        assistHandler.clear()
    }

    private void handleDirectoryInputs(TransformOutputProvider outputProvider,
                                       Collection<DirectoryInput> directoryInputs){
        directoryInputs.each {
            DirectoryInput input = it
            File out = outputProvider.getContentLocation(input.name, input.contentTypes,
                    input.scopes, Format.DIRECTORY)
            project.logger.error("input dir = " + input.file.path)
            FileUtils.copyDirectory(input.file, out)
            project.logger.error("add classPath = " + out.path)
            assistHandler.insertClassPath(out.path)
            if(input.changedFiles == null || input.changedFiles.isEmpty()){
                assistHandler.handleDirectory(out)
                return
            }
            input.changedFiles.keySet().each {
                Status status = input.changedFiles.get(it, Status.ADDED)
                switch (status){
                    case Status.ADDED:
                    case Status.CHANGED:
                        project.logger.error("changed = " + it.path)
                        assistHandler.handleFile(out, it)
                }
            }
        }
    }

    @Override
    public String getName() {
        return "printException"
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        if (project.getPlugins().hasPlugin(LibraryPlugin.class)) {
            return ImmutableSet.of(QualifiedContent.Scope.PROJECT)
        }
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    public boolean isIncremental() {
        return true
    }
}
