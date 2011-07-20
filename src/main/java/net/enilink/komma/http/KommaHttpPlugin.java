package net.enilink.komma.http;

import java.util.Dictionary;
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
	protected final static String environmentKeyHttpPort = "org.osgi.service.http.port"; //$NON-NLS-1$

	protected final static String serviceKeyHttpAddress = "http.address"; //$NON-NLS-1$
	protected final static String serviceKeyHttpPort = "http.port"; //$NON-NLS-1$
	protected final static String serviceKeyHttpScheme = "http.scheme"; //$NON-NLS-1$

	private static KommaHttpPlugin instance;

	public static final String PLUGIN_ID = "net.enilink.komma.http";

	ServiceTracker<HttpService, HttpService> defaultHttpServiceTracker;
	String httpServiceUrl;

	@Override
	public void start(BundleContext context) throws Exception {
		instance = this;
		super.start(context);

		defaultHttpServiceTracker = installDefaultHttpServiceTracker(context);

		// try to start some OSGi HTTP Service if not already running
		if (httpServiceUrl == null) {
			try {
				// try to start Jetty
				Class<?> jettyConfigurator = getBundle().loadClass(
						"org.eclipse.equinox.http.jetty.JettyConfigurator");

				Dictionary<String, Object> settings = new Hashtable<String, Object>();
				settings.put("http.port", 0);
				jettyConfigurator.getMethod("startServer", String.class,
						Dictionary.class).invoke(null, PLUGIN_ID, settings);
			} catch (Exception e) {
				// Jetty is not available or could not be started,
				// try to use Equinox HTTP service
				Bundle httpBundle = Platform
						.getBundle("org.eclipse.equinox.http");
				if (httpBundle != null) {
					// allow automatic selection of free port
					System.setProperty(environmentKeyHttpPort, "0");
					try {
						httpBundle.start(Bundle.START_TRANSIENT);
					} catch (Exception e2) {
						logError("Unable to start Equinox HTTP service.", e2);
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

		super.stop(context);
		instance = null;
	}

	public static KommaHttpPlugin getDefault() {
		return instance;
	}

	/**
	 * Logs an Error message with an exception.
	 */
	public static synchronized void logError(String message, Throwable ex) {
		if (message == null)
			message = ""; //$NON-NLS-1$
		Status errorStatus = new Status(IStatus.ERROR, PLUGIN_ID, IStatus.OK,
				message, ex);
		KommaHttpPlugin.getDefault().getLog().log(errorStatus);
	}

	ServiceTracker<HttpService, HttpService> installDefaultHttpServiceTracker(
			BundleContext context) throws Exception {
		String filterString = "(" + Constants.OBJECTCLASS + "="
				+ HttpService.class.getName() + ")";

		Filter filter = context.createFilter(filterString);
		ServiceTracker<HttpService, HttpService> defaultHttpServiceTracker = new ServiceTracker<HttpService, HttpService>(
				context, filter, null) {
			@Override
			public HttpService addingService(
					ServiceReference<HttpService> reference) {
				String scheme = (String) reference
						.getProperty(serviceKeyHttpScheme);
				if (scheme == null) {
					scheme = "http";
				}
				String address = (String) reference
						.getProperty(serviceKeyHttpAddress);
				if (address == null || "ALL".equals(address.toUpperCase())) {
					address = "127.0.0.1";
				}
				int port = Integer.valueOf(reference.getProperty(
						serviceKeyHttpPort).toString());

				httpServiceUrl = scheme + "://" + address + ":" + port;
				System.out.println("HTTP Service: " + httpServiceUrl);

				HttpService service = super.addingService(reference);
				registerResources(service);
				return service;
			}
		};
		defaultHttpServiceTracker.open();

		return defaultHttpServiceTracker;
	}

	void registerResources(HttpService httpService) {
		// register servlets and resources for HttpService
		try {
			httpService.registerServlet("/komma/content", new ContentServlet(),
					new Hashtable<String, String>(), null);
		} catch (ServletException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NamespaceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static String createHttpUrl(String resourceUrl) {
		String httpServiceUrl = getDefault().httpServiceUrl;
		if (httpServiceUrl != null) {
			return httpServiceUrl + "/komma/content/" + resourceUrl;
		}
		return resourceUrl;
	}
}
