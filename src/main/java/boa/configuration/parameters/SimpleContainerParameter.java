/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.configuration.parameters;

import boa.configuration.parameters.ui.ParameterUI;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import boa.utils.Utils;
import java.util.function.Consumer;

/**
 *
 * @author jollion
 */

public abstract class SimpleContainerParameter implements ContainerParameter {
    protected String name;
    protected ContainerParameter parent;
    protected List<Parameter> children;
    protected boolean isEmphasized;
    public SimpleContainerParameter(String name) {
        this.name=name;
    }
    protected String toolTipText;
    @Override
    public String getToolTipText() {
        return toolTipText;
    }
    @Override
    public <T extends Parameter> T setToolTipText(String txt) {
        this.toolTipText=txt;
        return (T)this;
    }
    @Override
    public boolean isEmphasized() {
        if (isEmphasized) return true;
        return getChildren().stream().anyMatch((child) -> (child.isEmphasized()));
    }
    @Override
    public <T extends Parameter> T setEmphasized(boolean isEmphasized) {
        this.getChildren().stream().filter(p->!p.isEmphasized()).forEach(p -> p.setEmphasized(isEmphasized));
        this.isEmphasized = isEmphasized;
        return (T) this;
    }
    protected void initChildren(List<Parameter> parameters) {
        if (parameters==null) {
            children = new ArrayList<Parameter>(0);
        } else {
            //children = new ArrayList<Parameter>(parameters.size());
            children=parameters;
            int idx = 0;
            for (Parameter p : parameters) {
                if (p!=null) p.setParent(this);
                else logger.warn("SCP: {} initChildren error: param null: {}, name: {}, type: {}", this.hashCode(), idx, name, getClass().getSimpleName());
                
                //if (p instanceof SimpleContainerParameter) ((SimpleContainerParameter)p).initChildList(); -> cf postLoad
                idx++;
            }
        }
    }
    
    protected void initChildren(Parameter... parameters) {
        if (parameters==null || parameters.length==0) {
            children = new ArrayList<>(0);
        } else {
            initChildren(new ArrayList<>(Arrays.asList(parameters)));
        }
    }
    
    protected abstract void initChildList();
    
    @Override
    public boolean isValid() {
        return getChildren().stream().noneMatch((child) -> (!child.isValid()));
    }
    
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof SimpleContainerParameter) {
            bypassListeners=true;
            SimpleContainerParameter otherP = (SimpleContainerParameter) other;
            if (!ParameterUtils.setContent(getChildren(), otherP.getChildren())) logger.warn("SCP: {}({}): different parameter length, they might not be well set: c:{}/src:{}", name, this.getClass().getSimpleName(), children.size(), otherP.children.size());
            bypassListeners=false;
        } else {
            throw new IllegalArgumentException("wrong parameter type");
        }
    }
    
    @Override
    public <T extends Parameter> T duplicate() {
        try {
            T p = (T) this.getClass().getDeclaredConstructor(String.class).newInstance(name);
            p.setContentFrom(this);
            ((SimpleContainerParameter)p).setListeners(listeners);
            return p;
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            logger.error("duplicate error:", ex);
        }
        return null;
    }
    
    @Override
    public String getName(){
        return name;
    }
    
    @Override
    public void setName(String name) {
        this.name=name;
    }
    
    @Override
    public ArrayList<Parameter> getPath() {
        return SimpleParameter.getPath(this);
    }
    
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof ContainerParameter) {
            ContainerParameter otherLP = (ContainerParameter)other;
            if (otherLP.getChildCount()==this.getChildCount()) {
                for (int i = 0; i<getChildCount(); i++) {
                    if (!((Parameter)this.getChildAt(i)).sameContent((Parameter)otherLP.getChildAt(i))) {
                        logger.debug("{}!={} class {}, children differ at {}", name, other.getName(), getClass().getSimpleName(), i);
                        return false;
                    }
                }
                return true;
            } else {
                logger.debug("{} != {} class {}, child number: {} vs {}", name, other.getName(), getClass().getSimpleName(), getChildCount(), other.getChildCount());
                return false;
            }
        } else return false;
    }

    @Override
    public void insert(MutableTreeNode child, int index) {}
    
    @Override
    public void remove(int index) {}

    @Override
    public void remove(MutableTreeNode node) {}

    @Override public void setUserObject(Object object) {this.name=object.toString();}

    @Override
    public void removeFromParent() {
        //logger.info("(container) removing node from parent:"+((Parameter)this).toString() +" total number: "+children.size());
        parent.remove(this);
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        if (newParent==null) parent = null;
        else parent=(ContainerParameter)newParent;
    }

    @Override
    public ContainerParameter getParent() {
        return parent;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }
    
    @Override
    public String toString() {return name;}
    
    @Override
    public String toStringFull() {return name+":"+Utils.toStringList(children, p->p.toStringFull());}
    
    @Override
    public ParameterUI getUI() {
        return null;
    }

    @Override
    public Parameter getChildAt(int childIndex) {
        return getChildren().get(childIndex);
    }

    @Override
    public int getChildCount() {
        return getChildren().size();
    }

    @Override
    public int getIndex(TreeNode node) {
        return getChildren().indexOf((Parameter)node);
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(getChildren());
    }
    
    public List<Parameter> getChildren() {
        if (children==null) this.initChildList();
        return children;
    }
    
    // listenable
    protected List<Consumer<Parameter>> listeners;
    boolean bypassListeners;
    public void addListener(Consumer<Parameter> listener) {
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(listener);
    }
    public void removeListener(Consumer<Parameter> listener) {
        if (listeners != null) listeners.remove(listener);
    }
    public void fireListeners() {
        if (listeners != null && !bypassListeners) for (Consumer<Parameter> pl : listeners) pl.accept(this);
    }
    public void setListeners(List<Consumer<Parameter>> listeners) {
        if (listeners==null) this.listeners=null;
        else this.listeners=new ArrayList<>(listeners);
    }
    
}
