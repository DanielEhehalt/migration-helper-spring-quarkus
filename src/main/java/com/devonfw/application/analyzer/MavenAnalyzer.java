package com.devonfw.application.analyzer;

import net.sf.mmm.code.impl.java.JavaContext;
import net.sf.mmm.code.impl.java.source.maven.JavaSourceProviderUsingMaven;
import net.sf.mmm.code.impl.java.source.maven.MavenDependencyCollector;
import net.sf.mmm.code.java.maven.impl.MavenBridgeImpl;

/**
 * TODO dehehalt This type ...
 *
 */
public class MavenAnalyzer {

  public MavenAnalyzer() {

  }

  /**
   * Tries to get the Java context by creating a new class loader of the input project that is able to load the input
   * file. We need this in order to perform reflection on the templates.
   *
   * @param inputFile input file the user wants to generate code from
   * @param inputProject input project where the input file is located. We need this in order to build the classpath of
   *        the input file
   * @return the Java context created from the input project
   */
  public static JavaContext getJavaContext(Path inputFile, Path inputProject) {

    String fqn = null;
    MavenUtil.resolveDependencies(inputProject);
    try {
      MavenDependencyCollector dependencyCollector = new MavenDependencyCollector(
          new MavenBridgeImpl(MavenUtil.determineMavenRepositoryPath().toFile()), false, true, null);
      JavaContext context = JavaSourceProviderUsingMaven.createFromLocalMavenProject(inputProject.toFile(),
          dependencyCollector);
      LOG.debug("Checking dependencies to exist.");

      if (dependencyCollector.asClassLoader() instanceof URLClassLoader) {
        for (URL url : dependencyCollector.asUrls()) {
          try {
            if (!Files.exists(Paths.get(url.toURI()))) {
              LOG.info("Found at least one maven dependency not to exist ({}).", url);
              MavenUtil.resolveDependencies(inputProject);

              // rerun collection
              context = JavaSourceProviderUsingMaven.createFromLocalMavenProject(inputProject.toFile(), true);
              break;
            }
          } catch (URISyntaxException e) {
            LOG.warn("Unable to check {} for existence", url, (LOG.isDebugEnabled() ? e : null));
          }
        }
        LOG.info("All dependencies exist on file system.");
      } else {
        LOG.debug("m-m-m classloader is instance of {}. Unable to check dependencies",
            dependencyCollector.asClassLoader().getClass());
      }
      fqn = getFQN(inputFile);
      context.getClassLoader().loadClass(fqn);
      return context;
    } catch (NoClassDefFoundError | ClassNotFoundException e) {
      throw new CobiGenRuntimeException("Compiled class " + fqn
          + " has not been found. Most probably you need to build project " + inputProject.toString() + ".", e);
    } catch (Exception e) {
      throw new CobiGenRuntimeException(
          "Transitive dependencies have not been found on your m2 repository (Maven). Please run 'mvn package' "
              + "in your input project in order to download all the needed dependencies.",
          e);
    }
  }

}
