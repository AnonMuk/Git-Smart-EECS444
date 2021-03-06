import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class RepositoryAnalysis {
	public static String FS = System.getProperty("file.separator");
	
	public static double ALLOWED_CALLS_PER_DAY = 19000;
	public static double SECONDS_PER_DAY = 86400;

	public static String resultsDirectory = FS+"results"+FS;
	public static String header = "Project_id,Project name,Project link,VT link,VT detection result,Scan Result,Propogation link,Propogation method\n";

	public static void main(String[] args) {
		if(args.length > 0){
			switch(args[0]){
			case "-h":
				System.out.println("Usage:\n\t-h: help\n\t-a [repoList filename]: analyze list of repos from file\n\t-s [github repo URL]: analyze single repo");
				break;
			case "-a":
				File repos = new File(args[1]);
				analyzeRepos(repos);
				break;
			case "-s":
				String repo = args[1];
				analyzeRepo(repo);
				break;
			default:
				System.out.println("Error: use -h flag to see usage");
				break;
			}
		} else {
			System.out.println("Error: use -h flag to see usage");
		}
	}
	
	public static void analyzeRepo(String repoUrl){
		System.out.println("downloading... "+repoUrl);
		RepositoryHandler rph = new RepositoryHandler(repoUrl);
		System.out.println("scanning...    "+repoUrl);
		VirusChecker vc = new VirusChecker(0, repoUrl, rph.repository);
		if(rph.repository != null){	
			vc.requestScan();
		}else{
			vc.setStart();
		}
		rph.close();
		long singleWaitTime = 30000;
		while(vc.getWaitTime() > VirusChecker.WAIT_TIME-singleWaitTime){};
		System.out.println("retrieving...  "+vc.url);
		System.out.println(vc.retrieveResponse().toCSV());
	}

	public static void analyzeRepos(File repoFile){
		File results = nextResultFile();
		writeToFile(results, header);
		
		long startTime = System.currentTimeMillis();
		long calls = 0;
		int resultsPrinted = 0;
		ArrayList<VirusChecker> checks = new ArrayList<VirusChecker>();
		
		int repos = getNumberOfRepos(repoFile);

		System.out.println("***Github Repository Analysis***\n");
		System.out.println("Analyzing "+repos+" repos in "+repoFile.getPath());
		System.out.println("Writing results to "+results.getPath());
		System.out.println();
		
		int scannedRepositories=0;
		while(resultsPrinted < checks.size() || scannedRepositories<repos){
			//Scan any unscanned repositories
			if(scannedRepositories<repos){
				String repo = readLine(repoFile, scannedRepositories);
				System.out.println("downloading... "+repo);
				RepositoryHandler rph = new RepositoryHandler(repo);
				flowControl(calls, startTime);
				System.out.println("scanning...    "+repo);
				VirusChecker vc = new VirusChecker(scannedRepositories, repo, rph.repository);
				if(rph.repository != null){	
					vc.requestScan();
				}else{
					vc.setStart();
				}
				checks.add(vc);
				calls += vc.calls;
				rph.close();
				scannedRepositories++;
			}
			
			//handle any unretrieved results
			//System.out.println("repo: "+checks.get(resultsPrinted).id+"\nwaitTime: "+checks.get(resultsPrinted).getWaitTime());
			for(int i=resultsPrinted; i<checks.size() && checks.get(i).getWaitTime()<0; i++){
				calls++;
				flowControl(calls, startTime);
				System.out.println("retrieving...  "+checks.get(i).url);
				VirusCheck response = checks.get(i).retrieveResponse();
				writeToFile(results, response.toCSV());
				resultsPrinted++;
			}
		}
		
		double timeSinceStart = secondsSinceStart(startTime);
		System.out.println("Calls: "+calls);
		System.out.println("Time: "+timeSinceStart);
		System.out.println("Rate (calls/day): "+(calls*SECONDS_PER_DAY)/timeSinceStart);
		System.out.println("Rate (calls/month): "+(calls*SECONDS_PER_DAY*30)/timeSinceStart);
		
	}

	public static double secondsSinceStart(long startTime) {
		return (System.currentTimeMillis() - startTime) / 1000.0;
	}
	
	public static int getNumberOfRepos(File file){
		BufferedReader reader;
		int repos = 0;
		try {
			FileReader freader = new FileReader(file);
			reader = new BufferedReader(freader);
			while(reader.readLine() != null){
				repos++;
			}
			reader.close();
		} catch (IOException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
		return repos;
	}
	
	public static String readLine(File file, int line){
		BufferedReader reader;
		String ret = "";
		try {
			FileReader freader = new FileReader(file);
			reader = new BufferedReader(freader);
			for(int i=0; i<line; i++){
				reader.readLine();
			}
			ret = reader.readLine();
			reader.close();
		} catch (IOException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
		return ret;
	}

	public static void writeToFile(File file, String string) {
		BufferedWriter writer = null;
		try {
			FileWriter fwriter = new FileWriter(file, true);
			writer = new BufferedWriter(fwriter);
			writer.write(string);
			writer.close();
		} catch (IOException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
	}

	public static File nextResultFile() {
		File resultsFolder = new File(System.getProperty("user.dir") + resultsDirectory);
		File[] resultFiles = resultsFolder.listFiles();
		int lastTrialNum = 0;
		
		if(resultFiles != null){
			for(File file : resultFiles){
				String[] spaceSplit = file.getName().split("_");
				if (spaceSplit.length > 1) {
					String[] dotSplit = spaceSplit[1].split("\\.");
					if (dotSplit.length > 0) {
						int trialNum = Integer.parseInt(dotSplit[0]);
						if(trialNum > lastTrialNum){
							lastTrialNum = trialNum;
						}
					}
				}
			}
			return new File(resultsFolder.getAbsolutePath() + FS + "Trial_" + (lastTrialNum + 1) + ".csv");
		}else{
			return new File(resultsFolder.getAbsolutePath() + FS + "Trial_1.csv");
		}
	}

	public static String resultsToString(String repository, VirusCheck result) {
		String string = "";
		string += "-" + result.getRepo() + ": " + result.getStatus() + " [" + result.response.positives + "/"
				+ result.response.total + "]\n";
		return string;
	}

	public static void flowControl(long calls, long startTime) {
		Double callsPerDay = (calls * SECONDS_PER_DAY) / secondsSinceStart(startTime);
		if (callsPerDay > ALLOWED_CALLS_PER_DAY) {
			double allowedCallsPerSecond = ALLOWED_CALLS_PER_DAY / SECONDS_PER_DAY;
			double waitTime = (calls / allowedCallsPerSecond) - secondsSinceStart(startTime);
			System.out.println("calls/day:" + callsPerDay);
			System.out.println("wait time: " + waitTime);
			try {
				Thread.sleep((long) Math.ceil(waitTime * 1000));
			} catch (InterruptedException e) {
				System.out.println(e.getMessage());
			}
		}
	}
}
