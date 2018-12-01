package es.zoocial.web;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.zoocial.tsa.Configuration;
import es.zoocial.tsa.KeystoreHandler;
import es.zoocial.tsa.KeystoreHandler.KeystoreModel;
import es.zoocial.tsa.Timestamper;
import es.zoocial.util.IOHelper;
import es.zoocial.util.StringHelper;

public class TimestampServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TimestampServlet.class);
	
	public static final String TIMESTAMP_QUERY_CONTENT_TYPE = "application/timestamp-query";
    public static final String TIMESTAMP_REPLY_CONTENT_TYPE = "application/timestamp-reply";
    public static final String TRANSFER_ENCODING_HEADER = "Content-Transfer-Encoding";
    public static final String TRANSFER_ENCODING_BASE64 = "BASE64";
    public static final String TRANSFER_ENCODING_BINARY = "BINARY";
    
    private Timestamper stamper;
    
    @Override
    public void init(ServletConfig config) throws ServletException {
    	super.init(config);
    	
    	String configurationFile = System.getProperty("configuration");
    	if (StringHelper.isEmpty(configurationFile)) {
        	ServletContext context = config.getServletContext();
        	if (context == null) {
        		throw new IllegalStateException("Could not find configuration file");
        	}
        	configurationFile = (String)context.getInitParameter("configuration");
    	}
    	
    	LOGGER.info(String.format("Configuring servlet with file '%s'", configurationFile));
    	
    	Configuration conf = new Configuration();
    	conf.loadConfiguration(configurationFile);
    	
    	KeystoreHandler store = new KeystoreHandler();
    	store.loadKeystore(KeystoreModel.fromMap(conf.getPropertySet("keystore")));
    	stamper = new Timestamper(store);
    	
    	LOGGER.info("Servlet launched");
    }
    
    @Override
    public void destroy() {
    	super.destroy();
    	LOGGER.info("Servlet stopped");
    }
    
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    		throws ServletException, IOException {
    	LOGGER.info(String.format("Invalid method GET from %s", req.getRemoteAddr()));
    	resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Only supports POST method");
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    		throws ServletException, IOException {
    	
    	if (!TIMESTAMP_QUERY_CONTENT_TYPE.equalsIgnoreCase(req.getContentType())) {
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "content type should be " + TIMESTAMP_QUERY_CONTENT_TYPE);
    		return;
    	}
    	if (req.getContentLength() == 0) {
    		LOGGER.error("timestamp request is empty");
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "timestamp request is empty");
    		return;
    	}
    	
    	TimeStampRequest rq;
    	try {
    		rq = parseTSRequest(req);
    	} catch (Exception e) {
    		LOGGER.error("could not read timestamp request", e);
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "could not read timestamp request");
    		return;
    	}
    	LOGGER.debug(String.format("TS Request received from %s", req.getRemoteAddr()));
    	TimeStampResponse stampResponse = stamper.timestamp(rq);
    	if (stampResponse == null) {
    		LOGGER.warn(String.format("TS Request received from %s is not acceptable", 
    				req.getRemoteAddr()));
    		resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Could not generate timestamp response");
    		return;
    	}
    	if (stampResponse.getTimeStampToken() == null) {
    		LOGGER.warn(String.format("TS Request received from %s is not acceptable", 
    				req.getRemoteAddr()));
    		resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Could not generate timestamp response");
    		return;
    	}
    	
    	
    	byte[] response = stampResponse.getEncoded();
    	if (isBase64(req)) {
    		resp.setHeader(TRANSFER_ENCODING_HEADER, TRANSFER_ENCODING_BASE64);
    		response = Base64.encode(response);
        	LOGGER.debug(String.format("Responding to %s is in base64", req.getRemoteAddr()));
    	} else {
    		resp.setHeader(TRANSFER_ENCODING_HEADER, TRANSFER_ENCODING_BINARY);
    		LOGGER.debug(String.format("Responding to %s is in binary mode", req.getRemoteAddr()));
    	}

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType(TIMESTAMP_REPLY_CONTENT_TYPE);
		resp.setContentLength(response.length);

    	BufferedOutputStream outputStream = null;
    	try {
    		outputStream = new BufferedOutputStream(resp.getOutputStream());
    		outputStream.write(response);
    	} finally {
    		IOHelper.closeQuietly(outputStream);
    	}
		LOGGER.debug(String.format("Response sent to %s", req.getRemoteAddr()));
    }
    
    
    private TimeStampRequest parseTSRequest(HttpServletRequest request) throws IOException {
    	byte[] tsRequest;
		BufferedInputStream is = null;
		try {
			tsRequest = new byte[request.getContentLength()];
			is = new BufferedInputStream(request.getInputStream());
			is.read(tsRequest);
		} finally {
			IOHelper.closeQuietly(is);
		}
    	if (isBase64(request)) {
    		tsRequest = Base64.decode(tsRequest);
    	}
    	return new TimeStampRequest(tsRequest);
    }
    
    
    private boolean isBase64(HttpServletRequest request) {
    	return TRANSFER_ENCODING_BASE64.equalsIgnoreCase(request.getHeader(TRANSFER_ENCODING_HEADER));    	      	
    }
    
}
