package de.europace.spring.boot.docker.publish

import de.gesellix.gradle.docker.DockerPlugin
import de.gesellix.gradle.docker.tasks.DockerBuildTask
import de.gesellix.gradle.docker.tasks.DockerPushTask
import de.gesellix.gradle.docker.tasks.DockerRmiTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME
import org.gradle.api.tasks.Copy

const val EXTENSION_NAME = "dockerPublish"

class DockerPublishPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val extension = project.extensions.findByName(EXTENSION_NAME) as? DockerPublishExtension ?: project.extensions.create(EXTENSION_NAME, DockerPublishExtension::class.java, project)

    fun dockerImageId() = "${getOrganisation(extension)}/${extension.imageName.get()}:${extension.imageTag.get()}"

    val prepareBuildContext = project.tasks.register("prepareBuildContext", Copy::class.java) {
      it.from(extension.dockerBuildContextSources)
      it.into(extension.dockerBuildContextDir)
    }

    val copyArtifact = project.tasks.register("copyArtifact", Copy::class.java) {
      it.dependsOn(project.tasks.getByName("bootJar"))
      it.dependsOn(prepareBuildContext)
      it.from(project.tasks.getByName(("bootJar")))
      it.into(extension.dockerBuildContextDir)
      it.rename { "application.jar" }
    }

    val buildImage = project.tasks.register("buildImage", DockerBuildTask::class.java) {
      it.dependsOn(copyArtifact)
      it.setBuildContextDirectory(extension.dockerBuildContextDir)
      it.imageName = dockerImageId()
      it.buildParams = mapOf("rm" to true, "pull" to true)
      it.enableBuildLog = true

      it.doLast {
        project.logger.info("Image built as ${dockerImageId()}")
      }
    }

    val rmiLocalImage = project.tasks.register("rmiLocalImage", DockerRmiTask::class.java) {
      it.imageId = dockerImageId()
    }

    val publishImage = project.tasks.register("publishImage", DockerPushTask::class.java) {
      it.dependsOn(buildImage)
      it.repositoryName = dockerImageId()
      it.finalizedBy(rmiLocalImage)
    }

    project.pluginManager.apply(PublishingPlugin::class.java)
    project.pluginManager.apply(DockerPlugin::class.java)
    project.tasks.named(PUBLISH_LIFECYCLE_TASK_NAME) {
      it.finalizedBy(publishImage)
    }
  }

  private fun getOrganisation(extension: DockerPublishExtension): String {
    return extension.organisation.getOrNull() ?: throw GradleException("organisation must be set")
  }
}
