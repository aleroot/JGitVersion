package hunternif.jgitversion;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.gitective.core.CommitFinder;
import org.gitective.core.CommitUtils;
import org.gitective.core.filter.commit.CommitCountFilter;

public class JGitVersionTask extends Task {
	private String dir;
	private String property;
	
	public void setDir(String dir) {
		this.dir = dir;
	}
	public void setProperty(String property) {
		this.property = property;
	}
	
	@Override
	public void execute() throws BuildException {
		try {
			Git git = Git.open(new File(dir));
			Repository repo = git.getRepository();
			
			// Find base commit between current branch and "master":
			String branch = repo.getBranch();
			RevCommit base = CommitUtils.getBase(repo, "master", branch);
			CommitCountFilter count = new CommitCountFilter();
			CommitFinder finder = new CommitFinder(repo).setFilter(count);
			finder.findBetween(branch, base);
			long commitsSinceBase = count.getCount();
			
			// Find tags in "master" before base commit:
			RevWalk rw = new RevWalk(repo);
			rw.markStart(base);
			rw.setRetainBody(false);
			Ref master = repo.getRef("master");
			List<Ref> masterAsList = Arrays.asList(master);
			List<Ref> tags = git.tagList().call();
			Map<RevCommit, Ref> masterTags = new HashMap<RevCommit, Ref>();
			for (Ref tag : tags) {
				tag = repo.peel(tag);
				RevCommit commit = rw.parseCommit(tag.getPeeledObjectId());
				// Only remember tags reachable from "master":
				if (!RevWalkUtils.findBranchesReachableFrom(commit, rw, masterAsList).isEmpty()) {
					masterTags.put(commit, tag);
				}
			}
			
			// Find the shortest distance in commits between base tag in "master":
			long commitsBetweenBaseAndTag = Long.MAX_VALUE;
			String tagName = "";
			for (RevCommit tagCommit : masterTags.keySet()) {
				count.reset();
				finder.findBetween(base, tagCommit);
				if (count.getCount() < commitsBetweenBaseAndTag) {
					commitsBetweenBaseAndTag = count.getCount();
					tagName = masterTags.get(tagCommit).getName();
				}
			}
			long commitsSinceLastMasterTag = commitsSinceBase + commitsBetweenBaseAndTag;
			
			// Construct version string:
			String version = branch.equals("master") ? "" : (branch + "-");
			if (tagName.startsWith("refs/tags/")) {
				tagName = tagName.substring("refs/tags/".length());
			}
			// v1.1 -> 1.1
			if (tagName.matches("v\\d+.*")) {
				tagName = tagName.substring(1);
			}
			version += tagName + "." + commitsSinceLastMasterTag;
			
			getProject().setProperty(property, version);
		} catch (Exception e) {
			throw new BuildException(e);
		}
	}
}
