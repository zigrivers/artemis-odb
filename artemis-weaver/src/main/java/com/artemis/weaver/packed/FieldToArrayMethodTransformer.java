package com.artemis.weaver.packed;

import static com.artemis.weaver.packed.InstructionMutator.on;

import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.artemis.meta.ClassMetadata;
import com.artemis.meta.ClassMetadataUtil;
import com.artemis.meta.FieldDescriptor;
import com.artemis.transformer.MethodTransformer;
import com.artemis.weaver.TypedOpcodes;

public class FieldToArrayMethodTransformer extends MethodTransformer implements Opcodes {

	private final ClassMetadata meta;
	private final String fieldDesc;
	private final List<String> dataFieldNames;
	private final TypedOpcodes opcodes;
	
	private static final boolean LOG = false;

	public FieldToArrayMethodTransformer(MethodTransformer mt, ClassMetadata meta, List<String> dataFieldNames) {
		super(mt);
		this.meta = meta;
		this.dataFieldNames = dataFieldNames;
		opcodes = new TypedOpcodes(meta);
		
		FieldDescriptor f = ClassMetadataUtil.instanceFields(meta).get(0);
		fieldDesc = f.desc;
	}
	
	
	@Override
	public boolean transform(MethodNode mn) {
		InsnList instructions = mn.instructions;
		String owner = meta.type.getInternalName();
		
		if (LOG) System.out.println("OWNER: " + owner + " " + mn.name);
		
		boolean shouldDoSetter = true;
		for (int i = 0; instructions.size() > i; i++) {
			AbstractInsnNode node = instructions.get(i);
			switch(node.getType()) {
				case AbstractInsnNode.FIELD_INSN:
					FieldInsnNode f = (FieldInsnNode)node;
					if (shouldDoSetter && isSettingFieldWithPrimitive(f)) {
						if (LOG) System.out.println(">> SETTING FIELD index=" + i);
						i = on(instructions, f)
							.insertAtOffset(2,
								new FieldInsnNode(GETSTATIC, owner, "$data", "[" + fieldDesc))
							.insertAtOffset(1,
								new FieldInsnNode(GETFIELD, owner, "$offset", "I"),
								new InsnNode(ICONST_0 + dataFieldNames.indexOf(f.name)),
								new InsnNode(IADD))
							.insertAtOffset(0,
								new InsnNode(opcodes.tASTORE()))
							.delete(0)
							.transform();
					} else if (!shouldDoSetter && isSettingFieldWithPrimitive(f)) {
						if (LOG) System.out.println(">> SETTING FIELD index=" + i);
						i = on(instructions, f)
							.insertAtOffset(0,
								new InsnNode(opcodes.tASTORE()))
							.delete(0)
							.transform();
					} else if (isSettingFieldWithObject(f)) {
						if (LOG) System.out.println(">> SETTING FIELD FROM OBJECT index=" + i);
						i = on(instructions, f)
							.insertAtOffset(3,
								new FieldInsnNode(GETSTATIC, owner, "$data", "[" + fieldDesc))
							.insertAtOffset(2,
								new FieldInsnNode(GETFIELD, owner, "$offset", "I"),
								new InsnNode(ICONST_0 + dataFieldNames.indexOf(f.name)),
								new InsnNode(IADD))
							.insertAtOffset(0, 
								new InsnNode(opcodes.tASTORE()))
							.delete(0)
							.transform();
					} else if (isModifyingFieldWithObject(f)) {
						if (LOG) System.out.println(">> SETTING-MODIFYING FIELD FROM OBJECT index=" + i);
						i = on(instructions, f)
							.insertAtOffset(6,
								new FieldInsnNode(GETSTATIC, owner, "$data", "[" + fieldDesc))
							.insertAtOffset(5,
								new FieldInsnNode(GETFIELD, owner, "$offset", "I"),
								new InsnNode(ICONST_0 + dataFieldNames.indexOf(f.name)),
								new InsnNode(IADD),
								new InsnNode(DUP2),
								new InsnNode(opcodes.tALOAD()))
							.insertAtOffset(0, 
								new InsnNode(opcodes.tASTORE()))
							.delete(5)
							.delete(4)
							.delete(0)
							.transform();
					} else if (isLoadingFromField(f)) {
						if (LOG) System.out.println("<< LOAD FIELD index=" + i);
						i = on(instructions, f)
							.insertAtOffset(2, 
								new FieldInsnNode(GETSTATIC, owner, "$data", "[" + fieldDesc))
							.insertAtOffset(0,
								new FieldInsnNode(GETFIELD, owner, "$offset", "I"),
								new InsnNode(ICONST_0 + dataFieldNames.indexOf(f.name)),
								new InsnNode(IADD),
								new InsnNode(DUP2),
								new InsnNode(opcodes.tALOAD()))
							.delete(1)
							.delete(0)
							.transform();
						shouldDoSetter = false;
					} else if (isGettingField(f)) {
						if (LOG) System.out.println("<< GETTING FIELD index=" + i);
						i = on(instructions, f)
							.insertAtOffset(1, 
								new FieldInsnNode(GETSTATIC, owner, "$data", "[" + fieldDesc))
							.insertAtOffset(0,
								new FieldInsnNode(GETFIELD, owner, "$offset", "I"),
								new InsnNode(ICONST_0 + dataFieldNames.indexOf(f.name)),
								new InsnNode(IADD),
								new InsnNode(opcodes.tALOAD()))
							.delete(0)
							.transform();
					}
					if (LOG) System.out.println("\tindex=" + i);
					break;
				default:
					break;
			}
		}
		
		return super.transform(mn);
	}

	private boolean isSettingFieldWithPrimitive(FieldInsnNode f) {
		return PUTFIELD == f.getOpcode() &&
			f.owner.equals(meta.type.getInternalName()) &&
			f.desc.equals(fieldDesc) &&
			hasInstanceField(meta, f.name) &&
			!isObjectAccess(f.getPrevious()) &&
			!isObjectAccess(f.getPrevious().getPrevious());
	}
	
	private boolean isSettingFieldWithObject(FieldInsnNode f) {
		return PUTFIELD == f.getOpcode() &&
			isObjectAccess(f.getPrevious()) &&
			f.owner.equals(meta.type.getInternalName()) &&
			f.desc.equals(fieldDesc) &&
			hasInstanceField(meta, f.name);
	}
	
	private boolean isModifyingFieldWithObject(FieldInsnNode f) {
		return PUTFIELD == f.getOpcode() &&
			isObjectAccess(f.getPrevious().getPrevious()) &&
			f.owner.equals(meta.type.getInternalName()) &&
			f.desc.equals(fieldDesc) &&
			hasInstanceField(meta, f.name);
	}


	private boolean isLoadingFromField(FieldInsnNode f) {
		return GETFIELD == f.getOpcode() &&
			DUP == f.getPrevious().getOpcode() &&
			!isObjectAccess(f.getNext().getNext()) &&
			f.owner.equals(meta.type.getInternalName()) &&
			f.desc.equals(fieldDesc) &&
			hasInstanceField(meta, f.name);
	}
	
	private boolean isGettingField(FieldInsnNode f) {
		return GETFIELD == f.getOpcode() &&
			DUP != f.getPrevious().getOpcode() &&
			f.owner.equals(meta.type.getInternalName()) &&
			f.desc.equals(fieldDesc) &&
			hasInstanceField(meta, f.name);
	}
	
	
	private static boolean hasInstanceField(ClassMetadata meta, String fieldName) {
		for (FieldDescriptor f : ClassMetadataUtil.instanceFields(meta)) {
			if (f.name.equals(fieldName))
				return true;
		}
		
		return false;
	}
	
	private boolean isObjectAccess(AbstractInsnNode n) {
		if (n == null) return false;
		
		int opcode = n.getOpcode();
		return 
			opcode == INVOKESPECIAL ||
			opcode == INVOKEVIRTUAL ||
			opcode == INVOKEINTERFACE ||
			(opcode == GETFIELD && !((FieldInsnNode)n).owner.equals(meta.type.getInternalName()));
	}
}
