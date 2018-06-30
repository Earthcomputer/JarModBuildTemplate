
import java.util.function.Function
import net.minecraftforge.fml.relauncher.Side
import org.objectweb.asm.*
import org.objectweb.asm.tree.*

class SideFilter extends FilterReader {
	
	private static class BoolPtr {
		boolean val
	}
	
	static boolean matchesSide(InputStream file, Side side) {
		def reader = new ClassReader(file)
		BoolPtr ret = new BoolPtr();
		ret.val = true
		reader.accept(new ClassVisitor(Opcodes.ASM5) {
			AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				if (descriptor == 'Lnet/minecraftforge/fml/relauncher/SideOnly;') {
					return new AnnotationVisitor(Opcodes.ASM5) {
						void visitEnum(String name, String desc, String value) {
							if (name == 'value' && desc == 'Lnet/minecraftforge/fml/relauncher/Side;')
								ret.val = side.name() == value
						}
					}
				} else {
					return null
				}
			}
		}, ClassReader.SKIP_CODE)
		return ret.val
	}
	
	SideFilter() {
	}
	SideFilter(Reader reader) {
		super(reader)
	}
	
	Side side
	Function<String, InputStream> fileSupplier
	private byte[] clazz
	private Reader clazzIn
	
	int read() {
		if (clazz == null)
			doRead()
		return clazzIn.read()
	}
	
	private void doRead() {
		// Read in raw bytes
		def baos = new ByteArrayOutputStream()
		def b
		while ((b = super.read()) != -1)
			baos.write(b)
		clazz = baos.toByteArray()
		
		// Transform the class
		def reader = new ClassReader()
		def node = new ClassNode()
		reader.accept(node, 0)
		filterClass(node);
		def writer = new ClassWriter(0)
		node.accept(writer)
		clazz = writer.toByteArray()
		clazzIn = new InputStreamReader(new ByteArrayInputStream(clazzIn), 'US-ASCII')
	}
	
	private void filterClass(ClassNode node) {
		// remove sideonly annotations
		checkAnnotations(node.visibleAnnotations)
		
		// filter fields
		if (node.fields != null) {
			def fieldItr = node.fields.iterator()
			while (fieldItr.hasNext()) {
				def field = fieldItr.next()
				if (!checkAnnotations(field.visibleAnnotations))
					fieldItr.remove()
			}
		}
		
		// filter methods
		if (node.methods != null) {
			def methodItr = node.methods.iterator()
			while (methodItr.hasNext()) {
				def method = methodItr.next()
				if (!checkAnnotations(method.visibleAnnotations))
					methodItr.remove()
			}
		}
		
		// filter inner classes
		if (node.innerClasses != null) {
			def innerClassItr = node.innerClasses.iterator()
			while (innerClassItr.hasNext()) {
				def innerClass = innerClassItr.next()
				if (!matchesSide(fileSupplier.apply(innerClass.name), side))
					innerClassItr.remove()
			}
		}
	}
	
	private boolean checkAnnotations(List<AnnotationNode> annotations) {
		def annItr = annotations.iterator()
		while (annItr.hasNext()) {
			def ann = annItr.next()
			if (ann.desc == 'Lnet/minecraftforge/fml/relauncher/SideOnly;') {
				for (int i = 0; i < ann.values.size(); i += 2) {
					if (ann.values.get(i) == 'value') {
						String[] val = ann.values.get(i + 1) as String[]
						if (val[0] == 'Lnet/minecraftforge/fml/relauncher/Side;') {
							annItr.remove()
							return val[1] == side.name()
						}
					}
				}
			}
		}
		return true
	}
}
