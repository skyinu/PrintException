package com.skyinu.wardhere

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.api.transform.Format
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.CtClass
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
        WardHereExtension callExtension = project.extensions.
                findByName(WardHereExtension.DSL_DOMAIN_NAME)
        if(callExtension.dumpAble){
            CtClass.debugDump = callExtension.dumpDir
        }
        assistHandler.init(callExtension)
        assistHandler.insertClassPath(project.android.bootClasspath[0].toString())
        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll()
        }
        try {
            collectClassPath(transformInvocation, outputProvider)
            transformInvocation.inputs.each {
                handleJarInputs(transformInvocation.isIncremental(), outputProvider, it.jarInputs)
                handleDirectoryInputs(transformInvocation.isIncremental(),
                        outputProvider, it.directoryInputs)
            }
        }catch(Exception ex){
            project.logger.error(ex.message)
        }
        assistHandler.clear()
        CtClass.debugDump = null
    }

    private void collectClassPath(TransformInvocation transformInvocation,
                                  TransformOutputProvider outputProvider){
        transformInvocation.inputs.each {
            it.jarInputs.each {
                assistHandler.insertClassPath(it.file.path)
            }
            it.directoryInputs.each {
                //TODO incremental
                DirectoryInput input = it
                File out = outputProvider.getContentLocation(input.name, input.contentTypes,
                        input.scopes, Format.DIRECTORY)
                FileUtils.copyDirectory(input.file, out)
                assistHandler.insertClassPath(out.path)
            }
        }
    }

    private void handleJarInputs(boolean  incremental,TransformOutputProvider outputProvider,
                                 Collection<JarInput> jarInputs){
        jarInputs.each {
            JarInput input = it
            File out = outputProvider.getContentLocation(input.name, input.contentTypes,
                    input.scopes, Format.JAR)
            if(!incremental){
                assistHandler.handleJarInput(input.file, out)
                return
            }
            switch (it.status){
                case Status.CHANGED:
                case Status.ADDED:
                    assistHandler.handleJarInput(input.file, out)
            }
        }
    }

    private void handleDirectoryInputs(boolean  incremental,TransformOutputProvider outputProvider,
                                       Collection<DirectoryInput> directoryInputs){
        directoryInputs.each {
            //TODO incremental
            DirectoryInput input = it
            File out = outputProvider.getContentLocation(input.name, input.contentTypes,
                    input.scopes, Format.DIRECTORY)
            assistHandler.handleDirectory(out)
        }
    }

    @Override
    public String getName() {
        return "wardhere"
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        if (project.getPlugins().hasPlugin(LibraryPlugin.class)) {
            return QualifiedContent.Scope.PROJECT
        }
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    public boolean isIncremental() {
        return true
    }
}
