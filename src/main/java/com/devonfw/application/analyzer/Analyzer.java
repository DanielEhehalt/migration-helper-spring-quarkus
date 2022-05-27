package com.devonfw.application.analyzer;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaSource;
import net.sf.mmm.code.impl.java.JavaContext;
import net.sf.mmm.code.impl.java.source.maven.JavaSourceProviderUsingMaven;
import net.sf.mmm.code.impl.java.source.maven.MavenDependencyCollector;
import net.sf.mmm.code.java.maven.impl.MavenBridgeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilities for analyzing java projects
 */
public class Analyzer {

    private static final Logger LOG = LoggerFactory.getLogger(Analyzer.class);

    /**
     * Iterates recursively over all java files from an entry point. Creates the classloader from each file and collects and counts all contained classes
     *
     * @param entryPoint   Directory as entrypoint
     * @param inputProject Input project where the input file is located
     * @param mavenRepo    Path of the maven repository. Necessary to load the dependencies of the project
     * @param result       HashMap with class names as key and number of occurrences
     * @return HashMap with package names as key and number of occurrences
     */
    public static HashMap<String, Integer> collectClassesRecursively(File entryPoint, Path inputProject, Path mavenRepo, HashMap<String, Integer> result) {

        File[] directories = entryPoint.listFiles(File::isDirectory);

        if (directories != null && directories.length > 0) {
            for (File directory : directories) {
                File[] files = new File(String.valueOf(directory)).listFiles(File::isFile);
                if (files != null && files.length > 0) {
                    for (File file : files) {

                        //Exclude non-java files and tests
                        String filename = file.getName();
                        if (filename.endsWith(".java") && !filename.toLowerCase().contains("test")) {

                            //Build Java context from file and project
                            JavaContext javaContext = Analyzer.getJavaContext(file.toPath(), inputProject, mavenRepo);

                            //Get Classloader from context and the in the classloader included packages
                            Package[] packages = javaContext.getClassLoader().getDefinedPackages();

                            //Get classes referenced by classloader
                            for (Package p : packages) {
                                Set<String> classes = Analyzer.findAllClassesUsingClassLoader(javaContext.getClassLoader(), p.toString().substring(8));
                                //Count occurrence of packages
                                for (String c : classes) {
                                    if (result.get(c) != null) {
                                        int quantity = result.get(c);
                                        result.replace(c, quantity + 1);
                                    } else {
                                        result.put(c, 1);
                                    }
                                }
                            }
                        }
                    }
                }
                Analyzer.collectClassesRecursively(directory, inputProject, mavenRepo, result);
            }
            return result;
        }
        return result;
    }

    /**
     * Iterates recursively over all java files from an entry point and collects all dependencies of the project
     *
     * @param entryPoint   Directory as entrypoint
     * @param inputProject Input project where the input file is located
     * @param mavenRepo    Path of the maven repository. Necessary to load the dependencies of the project
     * @param result       List of all dependencies of the project
     * @return List of all dependencies of the project
     */
    public static List<String> collectDependenciesRecursively(File entryPoint, Path inputProject, Path mavenRepo, List<String> result) {

        File[] directories = entryPoint.listFiles(File::isDirectory);

        if (directories != null && directories.length > 0) {
            for (File directory : directories) {
                File[] files = new File(String.valueOf(directory)).listFiles(File::isFile);
                if (files != null && files.length > 0) {
                    for (File file : files) {

                        //Exclude non-java files and tests
                        String filename = file.getName();
                        if (filename.endsWith(".java") && !filename.toLowerCase().contains("test")) {

                            //Build Java context from file and project
                            MavenDependencyCollector dependencyCollector = new MavenDependencyCollector(
                                    new MavenBridgeImpl(mavenRepo.toFile()), false, true, null);
                            JavaContext context = JavaSourceProviderUsingMaven.createFromLocalMavenProject(inputProject.toFile(),
                                    dependencyCollector);
                            try {
                                context.getClassLoader().loadClass(getFQN(file.toPath()));
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                            URL[] urls = dependencyCollector.asUrls();

                            //Compare and add dependencies
                            for (URL url : urls) {
                                if (!result.contains(url.toString())) {
                                    result.add(url.toString());
                                }
                            }
                        }
                    }
                }
                Analyzer.collectDependenciesRecursively(directory, inputProject, mavenRepo, result);
            }
            return result;
        }
        return result;
    }

    /**
     * Collect and count all packages recursively across all Java files from an entry point
     *
     * @param entryPoint   Directory as entrypoint
     * @param inputProject Input project where the input file is located
     * @param mavenRepo    Path of the maven repository. Necessary to load the dependencies of the project
     * @param result       HashMap with package names as key and number of occurrences
     * @return HashMap with package names as key and number of occurrences
     */
    public static HashMap<String, Integer> collectPackagesRecursively(File entryPoint, Path inputProject, Path mavenRepo, HashMap<String, Integer> result) {

        File[] directories = entryPoint.listFiles(File::isDirectory);

        if (directories != null && directories.length > 0) {
            for (File directory : directories) {
                File[] files = new File(String.valueOf(directory)).listFiles(File::isFile);
                if (files != null && files.length > 0) {
                    for (File file : files) {

                        //Exclude non-java files and tests
                        String filename = file.getName();
                        if (filename.endsWith(".java") && !filename.toLowerCase().contains("test")) {

                            //Build Java context from file and project
                            JavaContext javaContext = Analyzer.getJavaContext(file.toPath(), inputProject, mavenRepo);

                            //Get Classloader from context and the in the classloader included packages
                            Package[] packages = javaContext.getClassLoader().getDefinedPackages();

                            //Get classes referenced by classloader
                            for (Package p : packages) {
                                Set<String> classes = Analyzer.findAllClassesUsingClassLoader(javaContext.getClassLoader(), p.toString().substring(8));
                            }

                            //Count occurrence of packages
                            for (Package p : packages) {
                                if (result.get(p.toString()) != null) {
                                    int quantity = result.get(p.toString());
                                    result.replace(p.toString(), quantity + 1);
                                } else {
                                    result.put(p.toString(), 1);
                                }
                            }
                        }
                    }
                }
                Analyzer.collectPackagesRecursively(directory, inputProject, mavenRepo, result);
            }
            return result;
        }
        return result;
    }

    /**
     * Collect and count all import statements recursively across all java files from an entrypoint
     *
     * @param entryPoint Directory as entrypoint
     * @param results    HashMap with import statement as key and number of occurrences
     * @return HashMap with import statement as key and number of occurrences
     */
    public static HashMap<String, Integer> collectImportsRecursively(File entryPoint, HashMap<String, Integer> results) {

        File[] directories = entryPoint.listFiles(File::isDirectory);
        if (directories != null && directories.length > 0) {
            for (File directory : directories) {
                File[] files = new File(String.valueOf(directory)).listFiles(File::isFile);
                if (files != null && files.length > 0) {
                    for (File file : files) {
                        //Exclude non-java files and tests
                        String filename = file.getName();
                        if (filename.endsWith(".java") && !filename.toLowerCase().contains("test")) {
                            JavaProjectBuilder builder = new JavaProjectBuilder();
                            try {
                                builder.addSource(new FileReader(file));
                            } catch (FileNotFoundException e) {
                                throw new RuntimeException("File not found: " + file, e);
                            }
                            Collection<JavaSource> sources = builder.getSources();
                            for (JavaSource source : sources) {
                                List<String> importsFromSource = source.getImports();
                                for (String i : importsFromSource) {
                                    if (results.get(i) != null) {
                                        int quantity = results.get(i);
                                        results.replace(i, quantity + 1);
                                    } else {
                                        results.put(i, 1);
                                    }
                                }
                            }
                        }
                    }
                }
                Analyzer.collectImportsRecursively(directory, results);
            }
            return results;
        }
        return results;
    }

    /**
     * Loads all resources of a classloader and returns a list of the contained classes
     *
     * @param classLoader Classloader in which to search for classes
     * @param packageName Package name to generate full qualified names
     * @return All classes in the given classloader
     */
    public static Set<String> findAllClassesUsingClassLoader(ClassLoader classLoader, String packageName) {

        InputStream stream = classLoader.getResourceAsStream(packageName.replaceAll("[.]", "/"));
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        return reader.lines()
                .filter(line -> line.endsWith(".class"))
                .map(line -> packageName + "." + line.substring(0, line.lastIndexOf('.')))
                .collect(Collectors.toSet());
    }

    /**
     * Tries to get the Java context by creating a new class loader of the input project that is able to load the input
     * file.
     *
     * @param inputFile    File for which the context is created
     * @param inputProject Input project where the input file is located. Necessary to build the classpath from the input file
     * @param mavenRepo    Path of the maven repository. Necessary to load the dependencies of the project
     * @return Java context created from the input project
     */
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
    public static String getFQN(Path inputFile) {

        String simpleName = inputFile.getFileName().toString().replaceAll("\\.(?i)java", "");
        String packageName = getPackageName(inputFile.getParent(), "");

        return packageName + "." + simpleName;
    }

    /**
     * This method traverse the folder in reverse order from child to parent
     *
     * @param folder      parent input file
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
