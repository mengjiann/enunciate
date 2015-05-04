package org.codehaus.enunciate;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;

/**
 * Assembles the whole Enunciate app without compilation or packaging of the war.
 *
 * For use with the "war" packaging.
 *
 * @author Ryan Heaton
 */
@Mojo ( name = "assemble", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME )
public class AssembleMojo extends ConfigMojo {

  /**
   * The directory where the webapp is built.  If using this goal along with "war" packaging, this must be configured to be the
   * same value as the "webappDirectory" parameter to the war plugin.
   */
  @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}", property = "enunciate.webappDirectory")
  protected String webappDirectory;

  /**
   * Whether to force the "packaging" of the project to be "war" packaging.
   */
  @Parameter(defaultValue = "true")
  protected boolean forceWarPackaging = true;

  @Override
  public void execute() throws MojoExecutionException {
    if (skipEnunciate) {
      getLog().info("Skipping enunciate per configuration.");
      return;
    }

    if (forceWarPackaging && !"war".equalsIgnoreCase(this.project.getPackaging())) {
      throw new MojoExecutionException("The 'assemble' goal requires 'war' packaging.");
    }

    super.execute();

    //todo: figure out what else needs to happen. Do we even need this mojo anymore?
  }

}