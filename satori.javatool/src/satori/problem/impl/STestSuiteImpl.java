package satori.problem.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import satori.common.SAssert;
import satori.common.SDataStatus;
import satori.common.SId;
import satori.common.SIdReader;
import satori.common.SListener0;
import satori.common.SPair;
import satori.common.SReference;
import satori.common.SView;
import satori.data.STestSuiteData;
import satori.metadata.SInputMetadata;
import satori.metadata.SParametersMetadata;
import satori.problem.SParentProblem;
import satori.problem.STestSuiteReader;
import satori.problem.STestSuiteSnap;
import satori.task.STask;
import satori.task.STaskException;
import satori.task.STaskHandler;
import satori.test.impl.STestImpl;
import satori.test.impl.STestSuiteBase;

public class STestSuiteImpl implements STestSuiteReader {
	private STestSuiteSnap snap = null;
	private SId id;
	private SParentProblem problem;
	private String name;
	private String desc;
	private STestSuiteBase base;
	private SParametersMetadata dispatcher;
	private List<SParametersMetadata> accumulators;
	private SParametersMetadata reporter;
	private Map<SInputMetadata, Object> general_params;
	private Map<SPair<SInputMetadata, Long>, Object> test_params;
	
	private final SDataStatus status = new SDataStatus();
	private final List<SView> views = new ArrayList<SView>();
	private final List<SListener0> metadata_modified_listeners = new ArrayList<SListener0>();
	private final SReference reference = new SReference() {
		@Override public void notifyModified() { snapModified(); }
		@Override public void notifyDeleted() { snapDeleted(); }
	};
	private final SListener0 base_modified_listener = new SListener0() {
		@Override public void call() { notifyModified(); }
	};
	
	public STestSuiteSnap getSnap() { return snap; }
	@Override public boolean hasId() { return id.isSet(); }
	@Override public long getId() { return id.get(); }
	@Override public long getProblemId() { return problem.getId(); }
	@Override public String getName() { return name; }
	@Override public String getDescription() { return desc; }
	public STestSuiteBase getBase() { return base; }
	@Override public List<STestImpl> getTests() { return base.getTests(); }
	@Override public SParametersMetadata getDispatcher() { return dispatcher; }
	@Override public List<SParametersMetadata> getAccumulators() { return accumulators; }
	@Override public SParametersMetadata getReporter() { return reporter; }
	@Override public Map<SInputMetadata, Object> getGeneralParameters() { return Collections.unmodifiableMap(general_params); }
	public Object getGeneralParameter(SInputMetadata meta) { return general_params.get(meta); }
	@Override public Map<SPair<SInputMetadata, Long>, Object> getTestParameters() { return Collections.unmodifiableMap(test_params); }
	public Object getTestParameter(SInputMetadata meta, long test) { return test_params.get(new SPair<SInputMetadata, Long>(meta, test)); }
	public boolean isRemote() { return hasId(); }
	public boolean isModified() { return status.isModified(); }
	public boolean isOutdated() { return status.isOutdated(); }
	public boolean isProblemRemote() { return problem.hasId(); }
	public boolean hasNonremoteTests() {
		for (SIdReader test : getTests()) if (!test.hasId()) return false;
		return true;
	}
	
	private STestSuiteImpl() {}
	
	public static STestSuiteImpl create(STaskHandler handler, STestSuiteSnap snap, SParentProblem problem) throws STaskException {
		if (!snap.isComplete()) snap.reload(handler);
		STestSuiteImpl self = new STestSuiteImpl();
		self.snap = snap;
		self.snap.addReference(self.reference);
		self.id = new SId(snap.getId());
		self.problem = problem;
		self.name = snap.getName();
		self.desc = snap.getDescription();
		self.base = STestSuiteBase.create(handler, problem, snap.getTests());
		self.dispatcher = snap.getDispatcher();
		self.accumulators = snap.getAccumulators();
		self.reporter = snap.getReporter();
		self.general_params = snap.getGeneralParameters();
		self.test_params = snap.getTestParameters();
		return self;
	}
	public static STestSuiteImpl createNew(SParentProblem problem, STestSuiteBase base) {
		STestSuiteImpl self = new STestSuiteImpl();
		self.id = SId.unset();
		self.problem = problem;
		self.name = "";
		self.desc = "";
		self.base = base;
		base.setModifiedListener(self.base_modified_listener);
		self.dispatcher = null;
		self.accumulators = Collections.emptyList();
		self.reporter = null;
		self.general_params = new HashMap<SInputMetadata, Object>();
		self.test_params = new HashMap<SPair<SInputMetadata, Long>, Object>();
		return self;
	}
	
	private boolean checkTestList(List<? extends SIdReader> list1) {
		Iterator<? extends SIdReader> iter1 = list1.iterator();
		Iterator<? extends SIdReader> iter2 = base.getTests().iterator();
		while (iter1.hasNext() && iter2.hasNext()) {
			SIdReader test = iter2.next();
			if (!test.hasId() || test.getId() != iter1.next().getId()) return true;
		}
		if (iter1.hasNext() || iter2.hasNext()) return true;
		return false;
	}
	private boolean check(STestSuiteReader source) {
		SAssert.assertEquals(source.getId(), getId(), "Test suite ids don't match");
		SAssert.assertEquals(source.getProblemId(), getProblemId(), "Problem ids don't match");
		if (!source.getName().equals(name)) return true;
		if (!source.getDescription().equals(desc)) return true;
		if (checkTestList(source.getTests())) return true;
		if (source.getDispatcher() == null && dispatcher != null) return true;
		if (source.getDispatcher() != null && !source.getDispatcher().equals(dispatcher)) return true;
		if (!source.getAccumulators().equals(accumulators)) return true;
		if (source.getReporter() == null && reporter != null) return true;
		if (source.getReporter() != null && !source.getReporter().equals(reporter)) return true;
		if (!source.getGeneralParameters().equals(general_params)) return true;
		if (!source.getTestParameters().equals(test_params)) return true;
		return false;
	}
	
	private void snapModified() {
		if (!check(snap)) return;
		notifyOutdated();
	}
	private void snapDeleted() {
		snap = null;
		id = SId.unset();
		notifyOutdated();
	}
	
	public void setName(String name) {
		if (this.name.equals(name)) return;
		this.name = name;
		notifyModified();
	}
	public void setDescription(String desc) {
		if (this.desc.equals(desc)) return;
		this.desc = desc;
		notifyModified();
	}
	
	/*public boolean hasTest(long id) {
		for (SIdReader test : tests) if (test.hasId() && test.getId() == id) return true;
		return false;
	}
	public void addTest(STestImpl test) {
		SAssert.assertFalse(tests.contains(test), "Test already contained");
		tests.add(test);
		notifyModified();
	}
	public void addTest(STestImpl test, int index) {
		SAssert.assertFalse(tests.contains(test), "Test already contained");
		tests.add(index, test);
		notifyModified();
	}
	public void removeTest(STestImpl test) {
		SAssert.assertTrue(tests.contains(test), "Removing uncontained test");
		tests.remove(test);
		notifyModified();
	}
	public void moveTest(STestImpl test, int index) {
		SAssert.assertTrue(tests.contains(test), "Moving uncontained test");
		int old_index = tests.indexOf(test);
		if (index == old_index || index == old_index+1) return;
		tests.remove(test);
		if (old_index < index) --index;
		tests.add(index, test);
		notifyModified();
	}*/
	
	public void setDispatcher(SParametersMetadata dispatcher) {
		if (this.dispatcher == null && dispatcher == null) return;
		if (this.dispatcher != null && this.dispatcher.equals(dispatcher)) return;
		if (this.dispatcher != null) for (SInputMetadata im : this.dispatcher.getGeneralParameters()) general_params.remove(im);
		if (dispatcher != null) for (SInputMetadata im : dispatcher.getGeneralParameters()) {
			Object value = im.getDefaultValue();
			if (value != null) general_params.put(im, value);
		}
		this.dispatcher = dispatcher;
		notifyModified();
		callMetadataModifiedListeners();
	}
	public void setAccumulators(List<SParametersMetadata> accumulators) {
		if (this.accumulators.equals(accumulators)) return;
		for (SParametersMetadata pm : this.accumulators) if (!accumulators.contains(pm))
			for (SInputMetadata im : pm.getGeneralParameters()) general_params.remove(im);
		for (SParametersMetadata pm : accumulators) if (!this.accumulators.contains(pm))
			for (SInputMetadata im : pm.getGeneralParameters()) {
				Object value = im.getDefaultValue();
				if (value != null) general_params.put(im, value);
			}
		this.accumulators = accumulators;
		notifyModified();
		callMetadataModifiedListeners();
	}
	public void setReporter(SParametersMetadata reporter) {
		if (this.reporter == null && reporter == null) return;
		if (this.reporter != null && this.reporter.equals(reporter)) return;
		if (this.reporter != null) for (SInputMetadata im : this.reporter.getGeneralParameters()) general_params.remove(im);
		if (reporter != null) for (SInputMetadata im : reporter.getGeneralParameters()) {
			Object value = im.getDefaultValue();
			if (value != null) general_params.put(im, value);
		}
		this.reporter = reporter;
		notifyModified();
		callMetadataModifiedListeners();
	}
	
	public void setGeneralParameter(SInputMetadata meta, Object value) {
		Object old_value = general_params.get(meta);
		if (value == null && old_value == null) return;
		if (value != null && value.equals(old_value)) return;
		if (value != null) general_params.put(meta, value);
		else general_params.remove(meta);
		notifyModified();
	}
	public void setTestParameter(SInputMetadata meta, long test, Object value) {
		SPair<SInputMetadata, Long> key = new SPair<SInputMetadata, Long>(meta, test);
		Object old_value = test_params.get(key);
		if (value == null && old_value == null) return;
		if (value != null && value.equals(old_value)) return;
		if (value != null) test_params.put(key, value);
		else test_params.remove(key);
		notifyModified();
	}
	
	private void notifyModified() {
		status.markModified();
		updateViews();
	}
	private void notifyOutdated() {
		status.markOutdated();
		updateViews();
	}
	private void notifyUpToDate() {
		status.markUpToDate();
		updateViews();
	}
	
	public void addMetadataModifiedListener(SListener0 listener) { metadata_modified_listeners.add(listener); }
	public void removeMetadataModifiedListener(SListener0 listener) { metadata_modified_listeners.remove(listener); }
	private void callMetadataModifiedListeners() { for (SListener0 listener : metadata_modified_listeners) listener.call(); }
	
	public void addView(SView view) { views.add(view); }
	public void removeView(SView view) { views.remove(view); }
	private void updateViews() { for (SView view : views) view.update(); }
	
	public void reload(STaskHandler handler) throws STaskException {
		snap.reload(handler); //calls snapModified //TODO
		List<STestImpl> new_tests = STestSuiteBase.createTestList(handler, problem, snap.getTests()); //TODO
		name = snap.getName();
		desc = snap.getDescription();
		base.closeTests();
		base.setTestList(new_tests);
		dispatcher = snap.getDispatcher();
		accumulators = snap.getAccumulators();
		reporter = snap.getReporter();
		notifyUpToDate();
		callMetadataModifiedListeners();
	}
	public void create(final STaskHandler handler) throws STaskException {
		handler.execute(new STask() {
			@Override public void run() throws Exception {
				id = new SId(STestSuiteData.create(handler, STestSuiteImpl.this));
			}
		});
		notifyUpToDate();
		snap = STestSuiteSnap.create(problem.getTestList(), this);
		snap.addReference(reference);
		problem.getTestSuiteList().addTestSuite(snap);
	}
	public void save(final STaskHandler handler) throws STaskException {
		handler.execute(new STask() {
			@Override public void run() throws Exception {
				STestSuiteData.save(handler, STestSuiteImpl.this);
			}
		});
		notifyUpToDate();
		snap.set(this);
	}
	public void delete(final STaskHandler handler) throws STaskException {
		handler.execute(new STask() {
			@Override public void run() throws Exception {
				STestSuiteData.delete(handler, getId());
			}
		});
		problem.getTestSuiteList().removeTestSuite(snap);
		snap.notifyDeleted(); //calls snapDeleted
	}
	
	public void close() {
		if (snap == null) return;
		snap.removeReference(reference);
		snap = null;
	}
}
