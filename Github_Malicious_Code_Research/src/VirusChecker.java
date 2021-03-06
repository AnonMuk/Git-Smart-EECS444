import java.io.File;
import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

public class VirusChecker {
	public static String TEST_FILE = "\\testFiles\\install-tl-windows.exe";
	public static String SMALL_TEST_FILE = "\\testFiles\\LeBron.mp4";
	public static String BIG_TEST_FILE = "\\testFiles\\Brady.mp4";
	public static String MALICIOUS_TEST_FILE = "\\testFiles\\PracticalMalwareAnalysis-Labs\\PracticalMalwareAnalysis-Labs.exe";
	
	public static String APIKEY_TOKEN = "apikey";
	public static String FILE_TOKEN = "file";
	
	public static String VT_KEY = "7028ce2341049231c77c73409914a4e42c0d6693e162bf476fcde2991fbb4736";
	public static String VT_32MB_UPLOAD_URL = "https://www.virustotal.com/vtapi/v2/file/scan";
	public static String VT_220MB_REQUEST_URL = "https://www.virustotal.com/vtapi/v2/file/scan/upload_url?apikey="+VT_KEY;
	public static String VT_REPORT_URL = "https://www.virustotal.com/vtapi/v2/file/report?apikey="+VT_KEY+"&resource=";
	
	public static long WAIT_TIME = 300000;
	public static CloseableHttpClient httpClient = HttpClients.createDefault();

	public int id;
	public String url;
	public File file;
	public int calls = 0;
	public ScanResponse scanResponse = null;
	public ReportResponse results = null;
	public long startTime = -1;
	
	public VirusChecker(int id, String url, File file){
		this.id = id;
		this.url = url;
		this.file = file;
	}
	
	//Checks a file for its size and then uploads it for 
	public void requestScan(){
		//Set URL based on size of file to upload
		double fileMB = file.length() / (1024 * 1024);
		String filename = file.getName();
		
		String uploadRequestURL = "";
		if(fileMB < 32){
			uploadRequestURL = VT_32MB_UPLOAD_URL;
		} else if (fileMB < 220 ){
			HttpGet requestForUpload = new HttpGet(VT_220MB_REQUEST_URL);
			try {
				calls++;
				RequestForUploadResponse urlResponse = httpClient.execute(requestForUpload, requestForUploadResponseHandler);
				uploadRequestURL = urlResponse.upload_url;
			} catch (ClientProtocolException e) {
				System.out.println(e.getMessage());
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		} else {
			System.out.println(file.getName()+" TOO BIG TO SCAN");
			startTime = System.currentTimeMillis();
			return;
		}

		//Upload file for scanning
		HttpEntity data = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
				.addTextBody("apikey", VT_KEY)
				.addBinaryBody("file", file).build();
		HttpUriRequest scanRequest = RequestBuilder.post(uploadRequestURL).setEntity(data).build();
		try {
			calls++;
			ScanResponse scanResponse = httpClient.execute(scanRequest, ScanResponseHandler);
			if(scanResponse == null){
				System.out.println("ERROR: "+filename+" returned a null response");
			} else {
				startTime = System.currentTimeMillis();
				this.scanResponse = scanResponse;
			}
		} catch (ClientProtocolException e) {
			System.out.println(e.getMessage());
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}
	
	public void setStart(){
		startTime = System.currentTimeMillis();
	}
	
	public VirusCheck retrieveResponse(){
		if(scanResponse != null){
			HttpGet reportRequest = new HttpGet(getReportRequestURL(scanResponse.resource));
			ReportResponse reportResponse = null;
			try {
				reportResponse = httpClient.execute(reportRequest, reportResponseHandler);
			} catch (ClientProtocolException e) {
				System.out.println(e.getMessage());
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
			this.results = reportResponse;
			return new VirusCheck(id, url, reportResponse);
		} else {
			return new VirusCheck(id, url, null);
		}
	}
	
	public double getWaitTime(){
		if(startTime == -1){
			return 1234.0;
		}else{
			long elapsedTime = System.currentTimeMillis() - startTime;
			long waitMillis = WAIT_TIME - elapsedTime;
			return waitMillis/1000.0;
		}
	}
	
	public String toString(){
		if(results == null){
			return file.getName()+": in progress";
		}else{
			return results.toString();
		}
	}
	
	public static String getReportRequestURL(String resourceId){
		return VT_REPORT_URL+resourceId;
	}
	
	ResponseHandler<ScanResponse> ScanResponseHandler = response -> {
		int status = response.getStatusLine().getStatusCode();
		 if (status >= 200 && status < 300) {
		  HttpEntity entity = response.getEntity();
		  String responseJson = entity != null ? EntityUtils.toString(entity) : null;
		  Gson gson = new Gson();
		  ScanResponse uploadResponse = gson.fromJson(responseJson, ScanResponse.class);
		  return uploadResponse;
		 } else {
		  throw new ClientProtocolException("Unexpected response status: " + status);
		 }
	};
		
	ResponseHandler<RequestForUploadResponse> requestForUploadResponseHandler = response -> {
		int status = response.getStatusLine().getStatusCode();
		 if (status >= 200 && status < 300) {
		  HttpEntity entity = response.getEntity();
		  String responseJson = entity != null ? EntityUtils.toString(entity) : null;
		  Gson gson = new Gson();
		  RequestForUploadResponse urlResponse = gson.fromJson(responseJson, RequestForUploadResponse.class);
		  return urlResponse;
		 } else {
		  throw new ClientProtocolException("Unexpected response status: " + status);
		 }
	};
		
	ResponseHandler<ReportResponse> reportResponseHandler = response -> {
		int status = response.getStatusLine().getStatusCode();
	 if (status >= 200 && status < 300) {
		  HttpEntity entity = response.getEntity();
		  String responseJson = entity != null ? EntityUtils.toString(entity) : null;
		  Gson gson = new Gson();
		  ReportResponse reportResponse = gson.fromJson(responseJson, ReportResponse.class);
		  return reportResponse;
		 } else {
		  throw new ClientProtocolException("Unexpected response status: " + status);
		 }
	};
	
	private class RequestForUploadResponse{
		String upload_url;
	}
}
