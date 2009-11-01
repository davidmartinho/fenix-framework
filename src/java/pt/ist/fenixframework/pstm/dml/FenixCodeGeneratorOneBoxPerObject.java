package pt.ist.fenixframework.pstm.dml;

import java.io.PrintWriter;
import java.util.Iterator;

import dml.*;
import dml.runtime.Relation;

import pt.ist.fenixframework.pstm.OneBoxDomainObject;

public class FenixCodeGeneratorOneBoxPerObject extends FenixCodeGenerator {

    private static final String DO_STATE_SUPER = OneBoxDomainObject.DO_State.class.getName().replace('$', '.');

    protected DomainClass currentClass;

    public FenixCodeGeneratorOneBoxPerObject(CompilerArgs compArgs, DomainModel domainModel) {
        super(compArgs, domainModel);
    }

    @Override
    protected String getDomainClassRoot() {
        return OneBoxDomainObject.class.getName();
    }

    @Override
    protected void generateBaseClassBody(DomainClass domClass, PrintWriter out) {
        // this is a hack to help redefine the getSlotExpression method

        // the proper way of doing this would be to change the
        // protocol used in dml.CodeGenerator to distinguish between
        // slots corresponding to roles from others

        // but this allows us to test this code without having to
        // change the DML code
        currentClass = domClass;

        super.generateBaseClassBody(domClass, out);

        generateMethodGetRelationFor(domClass, out);
        generateMakeNewStateMethod(out);
        generateDOStateInnerClass(domClass, out);
    }

    protected void generateMethodGetRelationFor(DomainClass domClass, PrintWriter out) {
        printMethod(out, "protected", Relation.class.getName(), "get$$relationFor", makeArg("String", "attrName"));
        startMethodBody(out);

        for (Role role : domClass.getRoleSlotsList()) {
            if ((role.getName() != null) && (role.getMultiplicityUpper() != 1)) {
                print(out, "if (attrName.equals(\"");
                print(out, role.getName());
                print(out, "\")) return ");
                print(out, getRelationSlotNameFor(role));
                println(out, ";");
            }
        }

        println(out, "return super.get$$relationFor(attrName);");

        endMethodBody(out);        
    }


    @Override
    protected void generateSlots(Iterator slotsIter, PrintWriter out) {
        // do nothing
    }

    @Override
    protected void generateInitSlot(Slot slot, PrintWriter out) {
        // do nothing
    }

    @Override
    protected void generateRoleSlot(Role role, PrintWriter out) {
        // don't generate slots for roles anymore
        // but generate an accessor for collections instead
        if (role.getMultiplicityUpper() != 1) {
            generateRelationListGetter(role, out);
        }
    }

    protected void generateRelationListGetter(Role role, PrintWriter out) {
        onNewline(out);
        printMethod(out, "private", getRelationAwareTypeFor(role), makeRelationListGetterName(role.getName()));
        startMethodBody(out);
        
        printWords(out, "return", "get$$relationList(\"");
        print(out, role.getName());
        print(out, "\", ");
        print(out, getRelationSlotNameFor(role));
        println(out, ");");

        endMethodBody(out);
    }


    @Override
    protected void generateInitRoleSlot(Role role, PrintWriter out) {
        // don't init anymore
    }

    @Override
    protected String getRoleOneBaseType() {
        return RoleOne.class.getName();
    }

    @Override
    protected void generateGetSlotExpression(String slotName, PrintWriter out) {
        print(out, getSlotExpression(slotName));
    }

    @Override
    protected String getSlotExpression(String slotName) {
        Role role = currentClass.findRoleSlot(slotName);
        if ((role != null) && (role.getMultiplicityUpper() != 1)) {
            return makeRelationListGetterName(slotName) + "()";
        } else {
            return "((DO_State)this.get$obj$state(false))." + slotName;
        }
    }

    protected String makeRelationListGetterName(String roleName) {
        return "get$rl$" + roleName;
    }


    @Override
    protected void generateSetterBody(String setterName, String slotName, String typeName, PrintWriter out) {
        print(out, "((DO_State)this.get$obj$state(true)).");
        print(out, slotName);
        print(out, " = ");
        print(out, slotName);
        print(out, ";");
    }


    protected void generateDOStateInnerClass(DomainClass domClass, PrintWriter out) {
        onNewline(out);
        print(out, "protected static class DO_State extends ");

        String superclassName = getEntityFullName(domClass.getSuperclass());
        printWords(out, (superclassName == null) ? getDomainClassRoot() : superclassName);
        print(out, ".DO_State");

        newBlock(out);
        onNewline(out);
        // all the slots
        for (Slot slot : domClass.getSlotsList()) {
            printWords(out, "private", slot.getTypeName(), slot.getName());
            println(out, ";");
        }

        for (Role role : domClass.getRoleSlotsList()) {
            if ((role.getName() != null) && (role.getMultiplicityUpper() == 1)) {
                printWords(out, "private", getTypeFullName(role.getType()), role.getName());
                println(out, ";");
            }
        }

        // the copyTo method
        printMethod(out, "protected", "void", "copyTo", makeArg(DO_STATE_SUPER, "newState"));
        startMethodBody(out);
        println(out, "super.copyTo(newState);");
        println(out, "DO_State newCasted = (DO_State)newState;");
        for (Slot slot : domClass.getSlotsList()) {
            printWords(out, "newCasted." + slot.getName(), "=", "this." + slot.getName());
            println(out, ";");
        }

        for (Role role : domClass.getRoleSlotsList()) {
            if ((role.getName() != null) && (role.getMultiplicityUpper() == 1)) {
                printWords(out, "newCasted." + role.getName(), "=", "this." + role.getName());
                println(out, ";");
            }
        }
        endMethodBody(out);
        
        closeBlock(out);
    }


    protected void generateMakeNewStateMethod(PrintWriter out) {
        printMethod(out, "protected", DO_STATE_SUPER, "make$newState");
        startMethodBody(out);
        println(out, "return new DO_State();");
        endMethodBody(out);
    }

    @Override
    protected void generateDatabaseReader(DomainClass domClass, PrintWriter out) {
        newline(out);
        printMethod(out, "protected", "void", "readStateFromResultSet", 
                    makeArg("java.sql.ResultSet", "rs"),
                    makeArg(DO_STATE_SUPER, "state"));
        print(out, " throws java.sql.SQLException");
        startMethodBody(out);

        if (domClass.hasSuperclass()) {
            println(out, "super.readStateFromResultSet(rs, state);");
        }

        println(out, "DO_State castedState = (DO_State)state;");

        for (Slot slot : domClass.getSlotsList()) {
            generateOneSlotRsReader(out, slot.getName(), slot.getSlotType());
        }

        for (Role role : domClass.getRoleSlotsList()) {
            if ((role.getName() != null) && (role.getMultiplicityUpper() == 1)) {
                generateOneRoleSlotRsReader(out, role.getName());
            }
        }
        
        endMethodBody(out);
    }
    
    @Override
    protected void generateOneSlotRsReader(PrintWriter out, String name, ValueType type) {
        onNewline(out);
        print(out, "set$");
        print(out, name);
        print(out, "(");
        printRsReaderExpressions(out, type, convertToDBStyle(name), 0);
        print(out, ", state);");
    }

    @Override
    protected void generateOneRoleSlotRsReader(PrintWriter out, String name) {
        onNewline(out);

        print(out, "castedState.");
        print(out, name);
        print(out, " = ");

        print(out, RESULT_SET_READER_CLASS);
        print(out, ".readDomainObject(rs, \"OID_");
        print(out, convertToDBStyle(name));
        print(out, "\");");
    }


    @Override
    protected void generateInternalizationSetter(String name, ValueType type, PrintWriter out) {
        newline(out);
        print(out, "private final void set$");
        print(out, name);
        print(out, "(");

        ValueType vt = getExternalizationType(type);
        print(out, vt.getFullname());
        print(out, " arg0, ");
        print(out, DO_STATE_SUPER);
        print(out, " obj$state)");


        startMethodBody(out);
        print(out, "((DO_State)obj$state).");
        print(out, name);
        print(out, " = (");
        print(out, type.getFullname());
        print(out, ")(");

        if (FenixDomainModel.isNullableType(vt)) {
            print(out, "(arg0 == null) ? null : ");
        }

        print(out, getRsReaderExpression(type));
        print(out, ");");
        endMethodBody(out);            
    }


    protected String getObjStateExpression(String baseType, String prefix, String name, boolean forWriting) {
        return String.format("((%s.DO_State)%s.get$obj$state(%b)).%s", baseType, prefix, forWriting, name);
    }

    protected String getReadExpression(String baseType, String prefix, String name) {
        return getObjStateExpression(baseType, prefix, name, false);
    }

    protected String getLValueExpression(String baseType, String prefix, String name) {
        return getObjStateExpression(baseType, prefix, name, true);
    }


    @Override
    protected String getRoleArgs(Role role) {
        return "";
    }

    @Override
    protected void generateRoleClassGetter(Role role, Role otherRole, PrintWriter out) {

        String rType = getTypeFullName(role.getType());
        String oType = getTypeFullName(otherRole.getType());

        if (role.getMultiplicityUpper() == 1) {
            printMethod(out, "public", rType, "getValue", makeArg(oType, "o1"));
            startMethodBody(out);
            print(out, "return ");
            print(out, getReadExpression(otherRole.getType().getBaseName(), "o1", role.getName()));
            print(out, ";");
            endMethodBody(out);

            printMethod(out, "public", "void", "setValue", makeArg(oType, "o1"), makeArg(rType, "o2"));
            startMethodBody(out);
            print(out, getLValueExpression(otherRole.getType().getBaseName(), "o1", role.getName()));
            print(out, " = o2;");
            endMethodBody(out);
        } else {
            printMethod(out, 
                        "public", 
                        makeGenericType("dml.runtime.RelationBaseSet", rType), 
                        "getSet", 
                        makeArg(oType, "o1"));
            startMethodBody(out);
            print(out, "return ((");
            print(out, otherRole.getType().getBaseName());
            print(out, ")o1).");
            print(out, makeRelationListGetterName(role.getName()) + "()");
            print(out, ";");
            endMethodBody(out);
        }
    }
}
