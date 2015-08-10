package za.org.grassroot.webapp;

import za.org.grassroot.core.GrassRootApplication;


public class Application {

    public static void main(String[] args) throws Exception {
         new GrassRootApplication(GrassRootWebApplicationConfig.class).run(args);
    }

}
