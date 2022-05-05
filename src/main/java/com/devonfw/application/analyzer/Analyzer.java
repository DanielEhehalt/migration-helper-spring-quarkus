package com.devonfw.application.analyzer;

import net.sf.mmm.code.impl.java.JavaContext;
import net.sf.mmm.code.impl.java.source.maven.JavaSourceProviderUsingMaven;
import net.sf.mmm.code.impl.java.source.maven.MavenDependencyCollector;
import net.sf.mmm.code.java.maven.impl.MavenBridgeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TODO dehehalt This type ...
 */
public class Analyzer {

    private static final Logger LOG = LoggerFactory.getLogger(Analyzer.class);

    public Analyzer() {
    }

    public static JavaContext getJavaContext(Path inputFile, Path inputProject, Path mavenRepo) {

        String fqn = null;
        try {
            //Collect dependencies
            MavenDependencyCollector dependencyCollector = new MavenDependencyCollector(
                    new MavenBridgeImpl(mavenRepo.toFile()), false, true, null);
            JavaContext context = JavaSourceProviderUsingMaven.createFromLocalMavenProject(inputProject.toFile(),
                    dependencyCollector);

            //Check if all dependencies are present
            LOG.debug("Checking dependencies to exist.");
            if (dependencyCollector.asClassLoader() instanceof URLClassLoader) {
                for (URL url : dependencyCollector.asUrls()) {
                    try {
                        if (!Files.exists(Paths.get(url.toURI()))) {
                            LOG.info("Found at least one maven dependency not to exist ({}).", url);
                            break;
                        }
                    } catch (URISyntaxException e) {
                        LOG.warn("Unable to check {} for existence", url);
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
            throw new RuntimeException("Compiled class " + fqn
                    + " has not been found. Most probably you need to build project " + inputProject.toString() + ".", e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Transitive dependencies have not been found on your m2 repository (Maven). Please run 'mvn package' "
                            + "in your input project in order to download all the needed dependencies.",
                    e);
        }
    }

    /**
     * This method is traversing parent folders until it reaches java folder in order to get the FQN
     *
     * @param inputFile Java input file to retrieve FQN (Full Qualified Name)
     * @return qualified name with full package
     */
    private static String getFQN(Path inputFile) {

        String simpleName = inputFile.getFileName().toString().replaceAll("\\.(?i)java", "");
        String packageName = getPackageName(inputFile.getParent(), "");

        return packageName + "." + simpleName;
    }

    /**
     * This method traverse the folder in reverse order from child to parent
     *
     * @param folder parent input file
     * @param packageName the package name
     * @return package name
     */
    private static String getPackageName(Path folder, String packageName) {

        if (folder == null) {
            return null;
        }

        if (folder.getFileName().toString().toLowerCase().equals("java")) {
            String[] pkgs = packageName.split("\\.");

            packageName = pkgs[pkgs.length - 1];
            // Reverse order as we have traversed folders from child to parent
            for (int i = pkgs.length - 2; i > 0; i--) {
                packageName = packageName + "." + pkgs[i];
            }
            return packageName;
        }
        return getPackageName(folder.getParent(), packageName + "." + folder.getFileName().toString());
    }
}
