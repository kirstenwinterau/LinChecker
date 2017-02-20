/*******************************************************************************
 * Copyright (c) 2017 Microsoft Research. All rights reserved. 
 *
 * The MIT License (MIT)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy 
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software. 
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Contributors:
 *   Markus Alexander Kuppe - initial API and implementation
 ******************************************************************************/
package tlc2.tool;

import tla2sany.semantic.SemanticNode;
import tla2sany.semantic.SymbolNode;
import tlc2.value.Value;
import util.UniqueString;

public class PoisonousApple extends TLCState {

	private static final long serialVersionUID = -8489825469528624802L;

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#bind(util.UniqueString, tlc2.value.Value, tla2sany.semantic.SemanticNode)
	 */
	@Override
	public TLCState bind(UniqueString name, Value value, SemanticNode expr) {
		return null;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#bind(tla2sany.semantic.SymbolNode, tlc2.value.Value, tla2sany.semantic.SemanticNode)
	 */
	@Override
	public TLCState bind(SymbolNode id, Value value, SemanticNode expr) {
		return null;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#unbind(util.UniqueString)
	 */
	@Override
	public TLCState unbind(UniqueString name) {
		return null;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#lookup(util.UniqueString)
	 */
	@Override
	public Value lookup(UniqueString var) {
		return null;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#containsKey(util.UniqueString)
	 */
	@Override
	public boolean containsKey(UniqueString var) {
		return false;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#copy()
	 */
	@Override
	public TLCState copy() {
		return null;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#deepCopy()
	 */
	@Override
	public TLCState deepCopy() {
		return null;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#addToVec(tlc2.tool.StateVec)
	 */
	@Override
	public StateVec addToVec(StateVec states) {
		return null;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#deepNormalize()
	 */
	@Override
	public void deepNormalize() {
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#fingerPrint()
	 */
	@Override
	public long fingerPrint() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#allAssigned()
	 */
	@Override
	public boolean allAssigned() {
		return false;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#createEmpty()
	 */
	@Override
	public TLCState createEmpty() {
		return null;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#toString()
	 */
	@Override
	public String toString() {
		return null;
	}

	/* (non-Javadoc)
	 * @see tlc2.tool.TLCState#toString(tlc2.tool.TLCState)
	 */
	@Override
	public String toString(TLCState lastState) {
		return null;
	}
}
