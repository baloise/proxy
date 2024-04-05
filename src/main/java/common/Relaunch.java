package common;


import static java.lang.String.format;
import static java.lang.Thread.sleep;
import static java.nio.file.Files.move;
import static java.nio.file.Paths.get;
import static java.util.Arrays.asList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class Relaunch {

	public static void main(String[] args) throws InterruptedException, IOException {
		String oldApp = args[0];
		String newApp = args[1];
		File logFile = new File(args[2]);
		try(PrintStream log = new PrintStream(logFile)){
			String[] launchScript =  new String[args.length-3];
			System.arraycopy(args, 3, launchScript, 0, launchScript.length);
			log.println(format("%s Relaunching %s %s %s[%s in %s", new Date(), oldApp, newApp, logFile, asList(launchScript), new File(".")));
			if(!new File(newApp).exists()) {
				log.println(newApp + " does not exist");
				System.exit(1);
			}
			File oldAppFile = new File(oldApp);
			while(oldAppFile.exists() && !oldAppFile.delete()) {
				log.println(new Date() + " waiting to delete "+oldApp);
				sleep(1000);
			}
			move(get(newApp), get(oldApp));
			new ProcessBuilder(launchScript).start();
		}
	}

	public static File writeJar() throws IOException {
		return outputToJar(Relaunch.class);
	}
	
	private static File outputToJar(Class<?> ...classes) throws IOException {
        // courtesy of com.sun.org.apache.xalan.internal.xsltc.compiler.XSLTC.outputToJar()
        final Manifest manifest = new Manifest();
        final java.util.jar.Attributes atrs = manifest.getMainAttributes();
        atrs.put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.2");
        atrs.put(java.util.jar.Attributes.Name.MAIN_CLASS, classes[0].getName());

        final Map<String, Attributes> map = manifest.getEntries();
        final String now = (new Date()).toString();
        final java.util.jar.Attributes.Name dateAttr =
            new java.util.jar.Attributes.Name("Date");

        final File jarFile = File.createTempFile("launch", "jar");
        try(JarOutputStream jos =
            new JarOutputStream(new FileOutputStream(jarFile), manifest)){
	        for (Class<?> clazz : classes) {
	            final String className = clazz.getName().replace('.', '/')+".class";
	            final java.util.jar.Attributes attr = new java.util.jar.Attributes();
	            attr.put(dateAttr, now);
	            map.put(className, attr);
	            jos.putNextEntry(new JarEntry(className));
	            try(InputStream in = clazz.getResourceAsStream( clazz.getSimpleName()+".class")){
	    			in.transferTo(jos);
	    		}
	        }
        }
        return jarFile;
	}

}