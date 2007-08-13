/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 * 
 * This file is a derivative of code released by the University of
 * California under the terms listed below.  
 *
 * Refinement Analysis Tools is Copyright �2007 The Regents of the
 * University of California (Regents). Provided that this notice and
 * the following two paragraphs are included in any distribution of
 * Refinement Analysis Tools or its derivative work, Regents agrees
 * not to assert any of Regents' copyright rights in Refinement
 * Analysis Tools against recipient for recipient�s reproduction,
 * preparation of derivative works, public display, public
 * performance, distribution or sublicensing of Refinement Analysis
 * Tools and derivative works, in source code and object code form.
 * This agreement not to assert does not confer, by implication,
 * estoppel, or otherwise any license or rights in any intellectual
 * property of Regents, including, but not limited to, any patents
 * of Regents or Regents� employees.
 * 
 * IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT,
 * INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES,
 * INCLUDING LOST PROFITS, ARISING OUT OF THE USE OF THIS SOFTWARE
 * AND ITS DOCUMENTATION, EVEN IF REGENTS HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *   
 * REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE AND FURTHER DISCLAIMS ANY STATUTORY
 * WARRANTY OF NON-INFRINGEMENT. THE SOFTWARE AND ACCOMPANYING
 * DOCUMENTATION, IF ANY, PROVIDED HEREUNDER IS PROVIDED "AS
 * IS". REGENTS HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT,
 * UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 */
package com.ibm.wala.core.tests.demandpa;

import java.util.Collection;
import java.util.Iterator;

import junit.framework.TestCase;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.demandpa.alg.DemandRefinementPointsTo;
import com.ibm.wala.demandpa.alg.IDemandPointerAnalysis;
import com.ibm.wala.demandpa.alg.statemachine.DummyStateMachine;
import com.ibm.wala.demandpa.alg.statemachine.StateMachineFactory;
import com.ibm.wala.demandpa.flowgraph.IFlowLabel;
import com.ibm.wala.demandpa.util.MemoryAccessMap;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.util.Atom;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntSet;

public abstract class AbstractPtrTest extends TestCase {

  protected boolean debug = false;

  public static CGNode findMainMethod(CallGraph cg) {
    Descriptor d = Descriptor.findOrCreateUTF8("([Ljava/lang/String;)V");
    Atom name = Atom.findOrCreateUnicodeAtom("main");
    for (Iterator<? extends CGNode> it = cg.getSuccNodes(cg.getFakeRootNode()); it.hasNext();) {
      CGNode n = it.next();
      if (n.getMethod().getName().equals(name) && n.getMethod().getDescriptor().equals(d)) {
        return n;
      }
    }
    Assertions.UNREACHABLE("failed to find method");
    return null;
  }

  public static CGNode findStaticMethod(CallGraph cg, Atom name, Descriptor args) {
    for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext();) {
      CGNode n = it.next();
      // System.err.println(n.getMethod().getName() + " " +
      // n.getMethod().getDescriptor());
      if (n.getMethod().getName().equals(name) && n.getMethod().getDescriptor().equals(args)) {
        return n;
      }
    }
    Assertions.UNREACHABLE("failed to find method");
    return null;
  }

  public static CGNode findInstanceMethod(CallGraph cg, IClass declaringClass, Atom name, Descriptor args) {
    for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext();) {
      CGNode n = it.next();
      // System.err.println(n.getMethod().getDeclaringClass() + " " +
      // n.getMethod().getName() + " " + n.getMethod().getDescriptor());
      if (n.getMethod().getDeclaringClass().equals(declaringClass) && n.getMethod().getName().equals(name)
          && n.getMethod().getDescriptor().equals(args)) {
        return n;
      }
    }
    Assertions.UNREACHABLE("failed to find method");
    return null;
  }

  public static PointerKey getParam(CGNode n, String methodName, HeapModel heapModel) {
    IR ir = n.getIR();
    for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext();) {
      SSAInstruction s = it.next();
      if (s instanceof SSAInvokeInstruction) {
        SSAInvokeInstruction call = (SSAInvokeInstruction) s;
        if (call.getCallSite().getDeclaredTarget().getName().toString().equals(methodName)) {
          IntSet indices = ir.getCallInstructionIndices(((SSAInvokeInstruction) s).getCallSite());
          Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
          SSAInstruction callInstr = ir.getInstructions()[indices.intIterator().next()];
          Assertions.productionAssertion(callInstr.getNumberOfUses() == 1, "multiple uses for call");
          return heapModel.getPointerKeyForLocal(n, callInstr.getUse(0));
        }
      }
    }
    Assertions.UNREACHABLE("failed to find call to " + methodName + " in " + n);
    return null;
  }

  protected void doPointsToSizeTest(String scopeFile, String mainClass, int expectedSize) throws ClassHierarchyException {
    Collection<InstanceKey> pointsTo = getPointsToSetToTest(scopeFile, mainClass);
    if (debug) {
      System.err.println("points-to for " + mainClass + ": " + pointsTo);
    }
    assertEquals(expectedSize, pointsTo.size());
  }

  protected Collection<InstanceKey> getPointsToSetToTest(String scopeFile, String mainClass) throws ClassHierarchyException {
    final IDemandPointerAnalysis dmp = makeDemandPointerAnalysis(scopeFile, mainClass);

    // find the testThisVar call, and check the parameter's points-to set
    CGNode mainMethod = AbstractPtrTest.findMainMethod(dmp.getBaseCallGraph());
    PointerKey keyToQuery = AbstractPtrTest.getParam(mainMethod, "testThisVar", dmp.getHeapModel());
    Collection<InstanceKey> pointsTo = dmp.getPointsTo(keyToQuery);
    return pointsTo;
  }

  protected DemandRefinementPointsTo makeDemandPointerAnalysis(String scopeFile, String mainClass) throws ClassHierarchyException {
    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(scopeFile);
    // build a type hierarchy
    ClassHierarchy cha = ClassHierarchy.make(scope);

    // set up call graph construction options; mainly what should be considered
    // entrypoints?
    Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha, mainClass);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    final AnalysisCache analysisCache = new AnalysisCache();
    CallGraphBuilder cgBuilder = Util.makeRTABuilder(options, analysisCache, cha, scope);
    final CallGraph cg = cgBuilder.makeCallGraph(options);
    // System.err.println(cg.toString());

    MemoryAccessMap fam = new MemoryAccessMap(cg, false);
    SSAPropagationCallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(options, analysisCache, cha, scope);
    DemandRefinementPointsTo fullDemandPointsTo = new DemandRefinementPointsTo(cg, builder, fam, cha, options,
        getStateMachineFactory());

    return fullDemandPointsTo;
  }

  protected StateMachineFactory<IFlowLabel> getStateMachineFactory() {
    return new DummyStateMachine.Factory<IFlowLabel>();
  }

}