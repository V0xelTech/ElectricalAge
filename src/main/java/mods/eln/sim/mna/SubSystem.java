package mods.eln.sim.mna;

import mods.eln.misc.Profiler;
import mods.eln.misc.Utils;
import mods.eln.sim.mna.component.*;
import mods.eln.sim.mna.misc.*;
import mods.eln.sim.mna.process.TransformerInterSystemProcess;
import mods.eln.sim.mna.state.State;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class SubSystem {
    public ArrayList<Component> component = new ArrayList<Component>();
    public List<State> states = new ArrayList<State>();
    public LinkedList<IDestructor> breakDestructor = new LinkedList<IDestructor>();
    public ArrayList<SubSystem> interSystemConnectivity = new ArrayList<SubSystem>();
    ArrayList<ISubSystemProcessI> processI = new ArrayList<ISubSystemProcessI>();
    State[] statesTab;

    RootSystem root;

    double dt;
    boolean matrixValid = false;

    int stateCount;
    RealMatrix A;
    //RealMatrix I;
    boolean singularMatrix;

    double[][] AInvdata;
    double[] Idata;

    double[] XtempData;

    boolean breaked = false;

    ArrayList<ISubSystemProcessFlush> processF = new ArrayList<ISubSystemProcessFlush>();

    public RootSystem getRoot() {
        return root;
    }

    public SubSystem(RootSystem root, double dt) {
        this.dt = dt;
        this.root = root;
    }

    public void invalidate() {
        matrixValid = false;
    }

    public void addComponent(Component c) {
        component.add(c);
        c.addedTo(this);
        invalidate();
    }

    public void addState(State s) {
        states.add(s);
        s.addedTo(this);
        invalidate();
    }

    public void removeComponent(Component c) {
        component.remove(c);
        c.quitSubSystem();
        invalidate();
    }

    public void removeState(State s) {
        states.remove(s);
        s.quitSubSystem();
        invalidate();
    }

	/*public void removeAll() {
		for (Component c : component) {
			c.disconnectFromSubSystem();
		}
		for (State s : states) {
			s.disconnectFromSubSystem();
		}	
		invalidate();
	}*/

    public void removeProcess(ISubSystemProcessI p) {
        processI.remove(p);
        invalidate();
    }

    public void addComponent(Iterable<Component> i) {
        for (Component c : i) {
            addComponent(c);
        }
    }

    public void addState(Iterable<State> i) {
        for (State s : i) {
            addState(s);
        }
    }

    public void addProcess(ISubSystemProcessI p) {
        processI.add(p);
    }

    //double[][] getDataRef()

    public void generateMatrix() {
        stateCount = states.size();

        Profiler p = new Profiler();
        p.add("Inversse with " + stateCount + " state : ");

        A = MatrixUtils.createRealMatrix(stateCount, stateCount);
        //Adata = ((Array2DRowRealMatrix) A).getDataRef();
        // X = MatrixUtils.createRealMatrix(stateCount, 1); Xdata =
        // ((Array2DRowRealMatrix)X).getDataRef();
        //I = MatrixUtils.createRealMatrix(stateCount, 1);
        //Idata = ((Array2DRowRealMatrix) I).getDataRef();
        Idata = new double[stateCount];
        XtempData = new double[stateCount];
        {
            int idx = 0;
            for (State s : states) {
                s.setId(idx++);
            }
        }

        for (Component c : component) {
            c.applyTo(this);
        }

        //	org.apache.commons.math3.linear.

        try {
            //FieldLUDecomposition QRDecomposition  LUDecomposition RRQRDecomposition
            RealMatrix Ainv = new QRDecomposition(A).getSolver().getInverse();
            AInvdata = Ainv.getData();
            singularMatrix = false;
        } catch (Exception e) {
            singularMatrix = true;
            if (stateCount > 1) {
                int idx = 0;
                idx++;
                Utils.println("//////////SingularMatrix////////////");
            }
        }

        statesTab = new State[stateCount];
        statesTab = states.toArray(statesTab);

        matrixValid = true;

        p.stop();
        Utils.println(p);
    }

    public void addToA(State a, State b, double v) {
        if (a == null || b == null)
            return;
        A.addToEntry(a.getId(), b.getId(), v);
        //Adata[a.getId()][b.getId()] += v;
    }

    public void addToI(State s, double v) {
        if (s == null) return;
        Idata[s.getId()] = v;
        //Idata[s.getId()][0] += v;
    }

	/*
	 * public void pushX(){
	 * 
	 * }
	 */
	/*
	 * public void popX(){
	 * 
	 * }
	 */

    public void step() {
        stepCalc();
        stepFlush();
    }

    public void stepCalc() {
        //Profiler profiler = new Profiler();
        //profiler.add("generateMatrix");
        if (!matrixValid) {
            generateMatrix();
        }

        if (!singularMatrix) {
            //profiler.add("generateMatrix");
            for (int y = 0; y < stateCount; y++) {
                Idata[y] = 0;
            }
            //profiler.add("generateMatrix");
            for (ISubSystemProcessI p : processI) {
                p.simProcessI(this);
            }
            //profiler.add("generateMatrix");

            for (int idx2 = 0; idx2 < stateCount; idx2++) {
                double stack = 0;
                for (int idx = 0; idx < stateCount; idx++) {
                    stack += AInvdata[idx2][idx] * Idata[idx];
                }
                XtempData[idx2] = stack;
            }
            //Xtemp = Ainv.multiply(I);
        }
        //profiler.stop();
        //Utils.println(profiler);
    }

    public double solve(State pin) {
        if (!matrixValid) {
            generateMatrix();
        }

        if (!singularMatrix) {
            for (int y = 0; y < stateCount; y++)
                Idata[y] = 0;

            for (ISubSystemProcessI p : processI)
                p.simProcessI(this);

            int idx2 = pin.getId();
            double stack = 0;
            for (int idx = 0; idx < stateCount; idx++)
                stack += AInvdata[idx2][idx] * Idata[idx];

            return stack;
        }
        return 0;
    }

    //RealMatrix Xtemp;
    public void stepFlush() {
        if (!singularMatrix) {
            for (int idx = 0; idx < stateCount; idx++) {
                //statesTab[idx].state = Xtemp.getEntry(idx, 0);
                statesTab[idx].state = XtempData[idx];

            }
        } else {
            for (int idx = 0; idx < stateCount; idx++) {
                statesTab[idx].state = 0;
            }
        }

        for (ISubSystemProcessFlush p : processF) {
            p.simProcessFlush();
        }
    }

    public static void main(String[] args) {
        RootSystem root = new RootSystem(0.05, 1);
        SubSystem ss1 = new SubSystem(root, 0.05);
        SubSystem ss2 = new SubSystem(root, 0.05);
        SubSystem ss3 = new SubSystem(root, 0.05);

        root.systems.add(ss1);
        root.systems.add(ss2);
        root.systems.add(ss3);

        State s1 = new State(),
            s2 = new State(),
            s3 = new State(),
            s4 = new State(),
            s5 = new State(),
            s6 = new State();
        ss1.addState(s1);
        ss1.addState(s2);
        ss2.addState(s3);
        ss2.addState(s4);
        ss3.addState(s5);
        ss3.addState(s6);

        VoltageSource e1 = new VoltageSource("e1", s1, null).setU(10);
        VoltageSource e2 = new VoltageSource("e2", null, s6).setU(0);
        Resistor r1 = new Resistor().setR(6.05e-1);
        Resistor r2 = new Resistor().setR(1.01e-1);
        Resistor r3 = new Resistor().setR(100.101);

        ss1.addComponent(e1);
        ss1.addComponent(r1.connectTo(s1,s2));
        ss2.addComponent(r2.connectTo(s3,s4));
        ss3.addComponent(r3.connectTo(s5,s6));
        ss3.addComponent(e2);

        VoltageSource magicIn1 = new VoltageSource("magicIn1", s2, null).setU(0);
        VoltageSource magicOut1 = new VoltageSource("magicOut1", s3, null).setU(0);
        VoltageSource magicIn2 = new VoltageSource("magicIn2", s4, null).setU(0);
        VoltageSource magicOut2 = new VoltageSource("magicOut2", s5, null).setU(0);

        ss1.addComponent(magicIn1);
        ss2.addComponent(magicOut1);
        ss2.addComponent(magicIn2);
        ss3.addComponent(magicOut2);

        TransformerInterSystemProcess proc1 = new TransformerInterSystemProcess(s2, s3, magicIn1, magicOut1);
        TransformerInterSystemProcess proc2 = new TransformerInterSystemProcess(s4, s5, magicIn2, magicOut2);
        proc1.setRatio(4);
        proc2.setRatio(0.25);
        root.addProcess(proc1);
        root.addProcess(proc2);

        root.step();

        System.out.println("e1: V = " + e1.getU() + ", I = " + e1.getI());
        System.out.println("s1: V = " + s1.state);
        System.out.println("r1: Vd = " + r1.getU() + ", I = " + r1.getI());
        System.out.println("s2: V = " + s2.state);
        System.out.println("magicIn1: V = " + magicIn1.getU() + ", I = " + magicIn1.getCurrent());
        System.out.println("magicOut1: V = " + magicOut1.getU() + ", I = " + magicOut1.getCurrent());
        System.out.println("s3: V = " + s3.state);
        System.out.println("r2: Vd = " + r2.getU() + ", I = " + r2.getI());
        System.out.println("s4: V = " + s4.state);
        System.out.println("magicIn2: V = " + magicIn2.getU() + ", I = " + magicIn2.getCurrent());
        System.out.println("magicOut2: V = " + magicOut2.getU() + ", I = " + magicOut2.getCurrent());
        System.out.println("s5: V = " + s5.state);
        System.out.println("r3: Vd = " + r3.getU() + ", I = " + r3.getI());
        System.out.println("s6: V = " + s6.state);
        System.out.println("e2: V = " + e2.getU() + ", I = " + e2.getI());
    }

    public boolean containe(State state) {
        return states.contains(state);
    }

    public void setX(State s, double value) {
        s.state = value;
    }

    public double getX(State s) {
        return s.state;
    }

    public double getXSafe(State bPin) {
        return bPin == null ? 0 : getX(bPin);
    }

    public boolean breakSystem() {
        if (breaked) return false;
        while (!breakDestructor.isEmpty()) {
            breakDestructor.pop().destruct();
        }

        for (Component c : component) {
            c.quitSubSystem();
        }
        for (State s : states) {
            s.quitSubSystem();
        }

        if (root != null) {
            for (Component c : component) {
                c.returnToRootSystem(root);
            }
            for (State s : states) {
                s.returnToRootSystem(root);
            }
        }
        root.systems.remove(this);

        invalidate();

        breaked = true;
        return true;
    }

    public void addProcess(ISubSystemProcessFlush p) {
        processF.add(p);
    }

    public void removeProcess(ISubSystemProcessFlush p) {
        processF.remove(p);
    }

    public double getDt() {
        return dt;
    }

    static public class Th {
        public double R, U;

        public boolean isHighImpedance() {
            return R > 1e8;
        }

        @Override
        public String toString() {
            return "Th{" +
                "R=" + R +
                ", U=" + U +
                '}';
        }
    }

    public Th getTh(State d, VoltageSource voltageSource) {
        Th th = new Th();
        double originalU = voltageSource.getU();

        double otherU = originalU + 5;
        voltageSource.setU(otherU);
        double otherI = solve(voltageSource.getCurrentState());

        voltageSource.setU(originalU);
        double originalI = solve(voltageSource.getCurrentState());

        double Rth = (otherU - originalU) / (originalI - otherI);
        double Uth;
        //if(Double.isInfinite(d.Rth)) d.Rth = Double.MAX_VALUE;
        if (Rth > 1e19 || Rth < 0) {
            Uth = 0;
            Rth = 1e19;
        } else {
            Uth = otherU + Rth * otherI;
        }
        voltageSource.setU(originalU);

        th.R = Rth;
        th.U = Uth;

        if(Double.isNaN(th.U)) {
            th.U = originalU;
            th.R = MnaConst.highImpedance;
        }
        if (Double.isNaN(th.R)) {
            th.U = originalU;
            th.R = MnaConst.highImpedance;
        }

        return th;
    }

    public Th getTh(State d, CurrentSource currentSource) {
        Th th = new Th();
        double originalI = currentSource.getCurrent();

        currentSource.setCurrent(1);
        double n2p = d.getSubSystem().solve(d);

        currentSource.setCurrent(-1);
        double n2m = d.getSubSystem().solve(d);

        th.U = (n2p + n2m) / 2;
        th.R = n2p - th.U;

        if(Double.isNaN(th.U)) {
            th.U = 0;
            th.R = MnaConst.highImpedance;
        }
        if (Double.isNaN(th.R)) {
            th.U = 0;
            th.R = MnaConst.highImpedance;
        }

        currentSource.setCurrent(originalI);
        return th;
    }

    public String toString() {
        String str = "";
        for (Component c: component) {
            if (c != null)
                str += c.toString();
        }
        //str = component.size() + "components";
        return str;
    }

    public int componentSize() {
        return component.size();
    }
}
