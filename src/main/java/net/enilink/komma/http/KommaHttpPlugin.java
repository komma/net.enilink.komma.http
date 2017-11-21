package net.enilink.komma.http;

import java.util.Arrays;
import java.util.Hashtable;

import javax.servlet.ServletException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;

import net.enilink.komma.http.servlet.ContentServlet;

public class KommaHttpPlugin extends Plugin {

	public static final String PLUGIN_ID = "net.enilink.komma.http"; //$NON-NLS-1$

	protected final static String environmentKeyHttpPort = "org.osgi.service.http.port"; //$NON-NLS-1$

	protected final static String serviceKeyHttpScheme = "http.scheme"; //$NON-NLS-1$
	protected final static String serviceKeyHttpAddress = "http.address"; //$NON-NLS-1$
	protected final static String serviceKeyHttpPort = "http.port"; //$NON-NLS-1$

	protected static final String URL_PATH = "/eclipse-resources"; //$NON-NLS-1$

	private static KommaHttpPlugin instance;

	ServiceTracker<HttpService, HttpService> defaultHttpServiceTracker;
	String httpServiceUrl;
	boolean registered;

	@Override
	public void start(BundleContext context) throws Exception {
		instance = this;
		super.start(context);

		registered = false;
		defaultHttpServiceTracker = installDefaultHttpServiceTracker(context);

		// try to start some OSGi HTTP Service if not already running
		if (httpServiceUrl == null) {
			String port = System.getProperty(environmentKeyHttpPort);

			// Jetty is not available or could not be started,
			// try to use PAX Web or Equinox HTTP service
			for (String bundleName : Arrays.asList( //
					"org.eclipse.equinox.http.jetty", //$NON-NLS-1$
					"org.ops4j.pax.web.pax-web-jetty-bundle", //$NON-NLS-1$
					"org.ops4j.pax.web.pax-web-jetty", //$NON-NLS-1$
					"org.eclipse.equinox.http" //$NON-NLS-1$
			)) {
				Bundle httpBundle = Platform.getBundle(bundleName);
				if (httpBundle != null) {
					// allow automatic selection of free port
					if (port == null) {
						System.setProperty(environmentKeyHttpPort, "0"); //$NON-NLS-1$
					}
					try {
						httpBundle.start(Bundle.START_TRANSIENT);
						return;
					} catch (Exception e2) {
						logError("Unable to start HTTP server bundle: " + bundleName, e2);
					}
				}
			}
		}
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		if (defaultHttpServiceTracker != null) {
			defaultHttpServiceTracker.close();
			defaultHttpServiceTracker = null;
		}
		registered = false;
		super.stop(context);
		instance = null;
	}

	public static KommaHttpPlugin getDefault() {
		return instance;
	}

	public boolean isRegistered() {
		return registered;
	}

	/**
	 * Logs an Error message with an exception.
	 */
	public static synchronized void logError(String message, Throwable ex) {
		if (message == null)
			message = ""; //$NON-NLS-1$
		Status errorStatus = new Status(IStatus.ERROR, PLUGIN_ID, IStatus.OK, message, ex);
		KommaHttpPlugin.getDefault().getLog().log(errorStatus);
	}

	ServiceTracker<HttpService, HttpService> installDefaultHttpServiceTracker(BundleContext context) throws Exception {
		String filterString = "(" + Constants.OBJECTCLASS + "=" + HttpService.class.getName() + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Filter filter = context.createFilter(filterString);
		ServiceTracker<HttpService, HttpService> defaultHttpServiceTracker = new ServiceTracker<HttpService, HttpService>(context, filter, null) {
			@Override
			public HttpService addingService(ServiceReference<HttpService> reference) {
				String scheme = (String) reference.getProperty(serviceKeyHttpScheme);
				if (scheme == null) {
					scheme = "http"; //$NON-NLS-1$
				}
				String address = (String) reference.getProperty(serviceKeyHttpAddress);
				if (address == null || "ALL".equals(address.toUpperCase())) { //$NON-NLS-1$
					address = "127.0.0.1"; //$NON-NLS-1$
				}
				Object portValue = reference.getProperty(serviceKeyHttpPort);
				if (portValue == null) {
					portValue = reference.getProperty(environmentKeyHttpPort);
				}
				int port = portValue != null ? Integer.valueOf(portValue.toString()) : 0;

				String serviceUrl = scheme + "://" + address + ":" + port; //$NON-NLS-1$ //$NON-NLS-2$
				System.out.println("HTTP Service: " + serviceUrl);

				// TODO find a better way to determine if we are running in a server environment
				boolean isRapRunning = false;
				try {
					Bundle rapBundle = Platform.getBundle("org.eclipse.rap.ui"); //$NON-NLS-1$
					isRapRunning = rapBundle != null && (rapBundle.getState() & (Bundle.ACTIVE | Bundle.STARTING | Bundle.RESOLVED)) != 0;
				} catch (Throwable exception) {
					// Assume that it's not available.
				}
				if (isRapRunning) {
					// simply use absolute paths for serving resources when running in a web context
					httpServiceUrl = ""; //$NON-NLS-1$
				} else {
					httpServiceUrl = serviceUrl;
				}

				HttpService service = super.addingService(reference);
				registered = registerResources(service);
				return service;
			}
		};
		defaultHttpServiceTracker.open();

		return defaultHttpServiceTracker;
	}

	boolean registerResources(HttpService httpService) {
		// register servlets and resources for HttpService
		try {
			httpService.registerServlet(URL_PATH, new ContentServlet(), new Hashtable<String, String>(), httpService.createDefaultHttpContext());
			return true;
		} catch (ServletException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NamespaceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	public String getServiceUrl() {
		return httpServiceUrl;
	}

	public static String createHttpUrl(String resourceUrl) {
		String httpServiceUrl = getDefault().getServiceUrl();
		if (httpServiceUrl != null) {
			return httpServiceUrl + createUrlPath(resourceUrl);
		}
		return resourceUrl;
	}

	public static String createUrlPath(String resourceUrl) {
		return URL_PATH + "/" + resourceUrl; //$NON-NLS-1$
	}
}
