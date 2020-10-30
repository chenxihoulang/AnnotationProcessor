package io.github.yuweiguocn.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import io.github.yuweiguocn.annotation.CustomAnnotation;

/**
 * 参考:https://yuweiguocn.github.io/java-annotation-processor/
 * 通过AutoService自动注册注解处理器,
 * 否则要自己手动创建res->META-INF.services->javax.annotation.processing.Processor文件,
 * 并在文件中每一行写入一个注解处理器的全路径名称
 */
@AutoService(Processor.class)
//@SupportedAnnotationTypes("io.github.yuweiguocn.annotation.CustomAnnotation")
//@SupportedSourceVersion(SourceVersion.RELEASE_8)
//@SupportedOptions(CustomProcessor.CUSTOM_ANNOTATION)
public class CustomProcessor extends AbstractProcessor {
    /**
     * 编译选项参数的名称
     */
    public static final String CUSTOM_ANNOTATION = "yuweiguoCustomAnnotation";

    /**
     * Filer接口支持通过注解处理器创建新文件
     */
    private Filer filer;
    /**
     * Messager接口提供注解处理器用来报告错误消息、警告和其他通知的方式
     */
    private Messager messager;

    private List<String> result = new ArrayList<>();
    private int round;

    /**
     * 获取注解处理器能进行处理的注解
     * 因为Android平台可能会有兼容问题，建议使用重写getSupportedAnnotationTypes方法指定支持的注解类型
     *
     * @return
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotataions = new LinkedHashSet<String>();
        annotataions.add(CustomAnnotation.class.getCanonicalName());
        return annotataions;
    }

    /**
     * 支持的Java版本
     *
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * 获取支持的编译器选项参数名称
     *
     * @return
     */
    @Override
    public Set<String> getSupportedOptions() {
        Set<String> options = new LinkedHashSet<String>();
        options.add(CUSTOM_ANNOTATION);
        return options;
    }

    /**
     * 这是Processor接口中提供的一个方法，
     * 当我们编译程序时注解处理器工具会调用此方法并且提供实现ProcessingEnvironment接口的对象作为参数
     *
     * @param processingEnvironment
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        filer = processingEnvironment.getFiler();
        messager = processingEnvironment.getMessager();
    }

    /**
     * 通过实现Processor接口可以自定义注解处理器
     *
     * @param annotations 请求处理注解类型的集合（也就是我们通过重写getSupportedAnnotationTypes方法所指定的注解类型）
     * @param roundEnv    是有关当前和上一次 循环的信息的环境
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            //获取编译选项参数,在gradle中进行配置
            String resultPath = processingEnv.getOptions().get(CUSTOM_ANNOTATION);
            if (resultPath == null) {
                messager.printMessage(Diagnostic.Kind.ERROR, "No option " + CUSTOM_ANNOTATION +
                        " passed to annotation processor");
                return false;
            }

            round++;
            messager.printMessage(Diagnostic.Kind.NOTE, "round " + round + " process over " + roundEnv.processingOver());

            Iterator<? extends TypeElement> iterator = annotations.iterator();
            while (iterator.hasNext()) {
                messager.printMessage(Diagnostic.Kind.NOTE, "name is " + iterator.next().getSimpleName().toString());
            }

            //如果循环处理完成返回true，否则返回false
            if (roundEnv.processingOver()) {
                if (!annotations.isEmpty()) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "Unexpected processing state: annotations still available after processing over");
                    return false;
                }
            }

            if (annotations.isEmpty()) {
                return false;
            }

            Set<? extends Element> rootElements = roundEnv.getRootElements();
            for (Element rootElement : rootElements) {
                messager.printMessage(Diagnostic.Kind.NOTE, "rootElements", rootElement);
            }

            //获取所有被CustomAnnotation注解的元素
            for (Element element : roundEnv.getElementsAnnotatedWith(CustomAnnotation.class)) {
                if (element.getKind() != ElementKind.METHOD) {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            String.format("Only methods can be annotated with @%s", CustomAnnotation.class.getSimpleName()),
                            element);
                    return true; // 退出处理
                }

                //判断是否是public的方法
                if (!element.getModifiers().contains(Modifier.PUBLIC)) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must be public", element);
                    return true;
                }

                //方法代表一个可执行元素
                ExecutableElement execElement = (ExecutableElement) element;
                //获取方法所属的类型
                TypeElement classElement = (TypeElement) execElement.getEnclosingElement();

                result.add(classElement.getSimpleName().toString() + "#" + execElement.getSimpleName().toString());

                //获取方法参数
                List<? extends VariableElement> parameters = execElement.getParameters();
                for (VariableElement parameter : parameters) {
                    messager.printMessage(Diagnostic.Kind.NOTE, "parameter:", parameter);
                }
            }

            if (!result.isEmpty()) {
                generateFile(resultPath);
            } else {
                messager.printMessage(Diagnostic.Kind.WARNING, "No @CustomAnnotation annotations found");
            }
            result.clear();
        } catch (Exception e) {
            e.printStackTrace();
            messager.printMessage(Diagnostic.Kind.ERROR, "Unexpected error in CustomProcessor: " + e);
        }
        return true;
    }

    private void generateFileUseJavaPoet(String path) {
        BufferedWriter writer = null;
        try {
            int period = path.lastIndexOf('.');
            //获取包名
            String myPackage = period > 0 ? path.substring(0, period) : null;
            //获取类名
            String clazz = path.substring(period + 1);

            ParameterizedTypeName listStr = ParameterizedTypeName.get(List.class, String.class);
            FieldSpec annotationField = FieldSpec.builder(listStr, "sAnnotations")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .build();

            CodeBlock.Builder staticInitBlockBuilder = CodeBlock.builder()
                    .addStatement("$N=new $T<>()", annotationField, ArrayList.class);
            writeMethodLinesUseJavaPoet(staticInitBlockBuilder);

            MethodSpec methodSpec = MethodSpec.methodBuilder("getAnnotations")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(listStr)
                    .addStatement("return sAnnotations")
                    .build();

            TypeSpec clazzType = TypeSpec.classBuilder(clazz)
                    .addModifiers(Modifier.PUBLIC)
                    .addField(annotationField)
                    .addStaticBlock(staticInitBlockBuilder.build())
                    .addMethod(methodSpec)
                    .build();

            JavaFile javaFile = JavaFile.builder(myPackage, clazzType).build();

            JavaFileObject sourceFile = filer.createSourceFile(path);
            writer = new BufferedWriter(sourceFile.openWriter());
            writer.write(javaFile.toString());
        } catch (IOException e) {
            throw new RuntimeException("Could not write source for " + path, e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    //Silent
                }
            }
        }
    }

    private void writeMethodLinesUseJavaPoet(CodeBlock.Builder builder) throws IOException {
        for (int i = 0; i < result.size(); i++) {
            builder.addStatement("sAnnotations.add($S)", result.get(i));
        }
    }

    /**
     * 在指定全路径类名路径下生成文件
     *
     * @param path
     */
    private void generateFile(String path) {
        BufferedWriter writer = null;
        try {
            JavaFileObject sourceFile = filer.createSourceFile(path);
            int period = path.lastIndexOf('.');
            //获取包名
            String myPackage = period > 0 ? path.substring(0, period) : null;
            //获取类名
            String clazz = path.substring(period + 1);

            writer = new BufferedWriter(sourceFile.openWriter());
            if (myPackage != null) {
                writer.write("package " + myPackage + ";\n\n");
            }

            writer.write("import java.util.ArrayList;\n");
            writer.write("import java.util.List;\n\n");
            writer.write("/** This class is generated by CustomProcessor, do not edit. */\n");
            writer.write("public class " + clazz + " {\n");
            writer.write("    private static final List<String> ANNOTATIONS;\n\n");
            writer.write("    static {\n");
            writer.write("        ANNOTATIONS = new ArrayList<>();\n\n");
            writeMethodLines(writer);
            writer.write("    }\n\n");
            writer.write("    public static List<String> getAnnotations() {\n");
            writer.write("        return ANNOTATIONS;\n");
            writer.write("    }\n\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new RuntimeException("Could not write source for " + path, e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    //Silent
                }
            }
        }
    }

    private void writeMethodLines(BufferedWriter writer) throws IOException {
        for (int i = 0; i < result.size(); i++) {
            writer.write("        ANNOTATIONS.add(\"" + result.get(i) + "\");\n");
        }
    }

}
