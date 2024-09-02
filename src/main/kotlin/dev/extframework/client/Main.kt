package dev.extframework.client

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import com.github.ajalt.clikt.core.subcommands
import dev.extframework.boot.archive.*
import dev.extframework.common.util.resolve
import dev.extframework.extloader.environment.ExtraAuditorsAttribute
import dev.extframework.extloader.work
import dev.extframework.extloader.workflow.Workflow
import dev.extframework.extloader.workflow.WorkflowContext
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.environment.ValueAttribute
import dev.extframework.internal.api.environment.extract
import dev.extframework.internal.api.target.ApplicationTarget
import java.nio.file.Path
import kotlin.system.exitProcess

internal fun getHomedir(): Path {
    return Path.of(System.getProperty("user.home")) resolve ".extframework"
}

public fun main(args: Array<String>) {
    val command = ProductionCommand()
    command.subcommands(DevCommand()).main(args)

    val launchContext = command.launchContext

    launch(BootLoggerFactory()) {
        val launchInfo: LaunchInfo<*> = launchContext.launchInfo.value

        val environment = ExtensionEnvironment()
        work(
            launchContext.options.workingDir,
            launchContext.archiveGraph,
            launchContext.dependencyTypes,
            launchInfo.context,
            launchInfo.workflow as Workflow<WorkflowContext>,
            AppTarget(
                launchInfo.classloader,
                VERSION_REGEX.matchEntire(
                    launchContext.options.version
                )!!.groups["version"]!!.value
            ),
            environment.apply {
                plusAssign(ExtraAuditorsAttribute(launchContext.extraAuditors))
                plusAssign(
                    ValueAttribute(
                        launchContext.launchInfo.value.mappingNS,
                        ValueAttribute.Key("mapping-target")
                    )
                )
            }
        )().merge()

        val app = environment[ApplicationTarget].extract().node.handle!!.classloader

        val mainClass = app.loadClass(
            launchInfo.mainClass
        )

        mainClass.getMethod("main", Array<String>::class.java).invoke(null, launchInfo.args)
    }

    exitProcess(0)
}
