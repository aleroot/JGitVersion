package hunternif.jgitversion;

import java.io.File;

public class TestGit {
	public static void main(String[] args) {
		try {
			System.out.println(JGitVersionTask.getProjectVersion(new File(".")));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
