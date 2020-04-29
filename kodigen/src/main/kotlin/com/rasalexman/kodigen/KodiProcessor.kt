package com.rasalexman.kodigen

import com.google.auto.service.AutoService
import com.rasalexman.kodi.annotations.BindProvider
import com.rasalexman.kodi.annotations.BindSingle
import com.rasalexman.kodi.annotations.IgnoreInstance
import com.rasalexman.kodi.annotations.WithInstance
import com.rasalexman.kodi.core.throwKodiException
import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.MirroredTypeException
import javax.tools.Diagnostic
import kotlin.reflect.KClass

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor::class)
class KodiAnnotationProcessor : AbstractProcessor() {

    companion object {

        private const val INSTANCE_TYPE_SINGLE = "single"
        private const val INSTANCE_TYPE_PROVIDER = "provider"

        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        private const val KODI_PACKAGE_PATH = "com.rasalexman.kodi.core"
        private const val KODI_GENERATED_PATH = "com.kodi.generated.modules."

        private const val DEFAULT_MODULE_NAME = "Module"
        private const val KODI_DEFAULT_MODULE_NAME = "kodi"
        private const val KODI_MODULE_PROPERTY_TYPE = "IKodiModule"

        private const val KODI_MEMBER_MODULE = "kodiModule"
        private const val KODI_MEMBER_BIND = "bind"
        private const val KODI_MEMBER_AT = "at"
        private const val KODI_MEMBER_WITH = "with"
        private const val KODI_MEMBER_INSTANCE = "instance"

        private const val KODI_TAG_PATTERN = "tag = %S"
        private const val KODI_SCOPE_PATTERN = "scope = %S"

        private const val KODI_ERROR_PACKAGE_NAME = "java."


        private const val ERROR_ANNOTATION_WITHOUT_PROPERTY_WITH =
                "You cannot use `@%s` annotation property `toClass: KClass<out Any>` to bind on method %s with java classes. Please specify it with `T::class`"
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(
                BindSingle::class.java.name,
                BindProvider::class.java.name
        )
    }

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val startTime = System.currentTimeMillis()
        println("KodiAnnotationProcessor started")
        processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        val modulesMap = mutableMapOf<String, MutableList<KodiBindData>>()
        collectAnnotationData(roundEnv.getElementsAnnotatedWith(BindProvider::class.java), modulesMap, ::getDataFromBindProvider)
        collectAnnotationData(roundEnv.getElementsAnnotatedWith(BindSingle::class.java), modulesMap, ::getDataFromBindSingle)
        modulesMap.forEach(::processModules)
        println("KodiAnnotationProcessor finished in `${System.currentTimeMillis() - startTime}` ms")
        return false
    }

    private fun processModules(moduleName: String, moduleElements: List<KodiBindData>) {
        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: return

        val lowerModuleName = moduleName.apply { this[0].toLowerCase() }
        val packageName = "$KODI_GENERATED_PATH${moduleName.toLowerCase()}"
        val fileName = "${lowerModuleName.capitalize()}$DEFAULT_MODULE_NAME"
        val fileBuilder= FileSpec.builder(packageName, fileName)

        val codeInitializer = buildCodeBlock {
            add("%M {", MemberName(KODI_PACKAGE_PATH, KODI_MEMBER_MODULE))
            moduleElements.forEach { bindingData ->
                val element = bindingData.element
                val toClass = bindingData.toClass
                val toPack = bindingData.toPack
                val instanceType = bindingData.instanceType
                val scope = bindingData.scope
                val tag = bindingData.tag

                if(toClass.contains(KODI_ERROR_PACKAGE_NAME)) {
                    throwKodiException<IllegalStateException>("You cannot use java class $toClass as binding class cause it's goes to unexpected graph.")
                }


                add(" \n")
                add("%M", MemberName(KODI_PACKAGE_PATH, KODI_MEMBER_BIND))
                add("<%T>", ClassName(toPack, toClass))
                if(tag.isNotEmpty()) {
                    add("($KODI_TAG_PATTERN) ", tag)
                } else {
                    add("() ")
                }

                if(scope.isNotEmpty()) {
                    add("%M %S ", MemberName(KODI_PACKAGE_PATH, KODI_MEMBER_AT), scope)
                }

                add("%M ", MemberName(KODI_PACKAGE_PATH, KODI_MEMBER_WITH))
                add("%M {", MemberName(KODI_PACKAGE_PATH, instanceType))
                add(" \n")

                if(element.kind == ElementKind.METHOD) {
                    this.bindMethodElement(element)
                } else {
                    this.bindInstanceElement(element)
                }
                add("\n}\n")

            }
            add("}")
        }

        val typeName = ClassName(KODI_PACKAGE_PATH, KODI_MODULE_PROPERTY_TYPE)
        val moduleProperty = PropertySpec
                .builder("$lowerModuleName$DEFAULT_MODULE_NAME", typeName)
                .initializer(codeInitializer).build()
        fileBuilder.addProperty(moduleProperty)
        val file = fileBuilder.build()
        val createdFile = File(kaptKotlinGeneratedDir)
        file.writeTo(createdFile)
    }

    private fun getDataFromBindSingle(element: Element): KodiBindData {
        val annotation = element.getAnnotation(BindSingle::class.java)
        val toClassPackageAndClass = element.getAnnotationClassValue<BindSingle> { toClass }.toString()
        val (packName, className) = toClassPackageAndClass.getPackAndClass()
        return KodiBindData(
                element = element,
                toModule = annotation.toModule,
                toPack = packName,
                toClass = className,
                instanceType =INSTANCE_TYPE_SINGLE,
                scope = annotation.atScope,
                tag = annotation.toTag
        )
    }

    private fun getDataFromBindProvider(element: Element): KodiBindData {
        val annotation = element.getAnnotation(BindProvider::class.java)
        val packageAndClass = element.getAnnotationClassValue<BindProvider> { toClass }.toString()
        val (packName, className) = packageAndClass.getPackAndClass()
        return KodiBindData(
                element = element,
                toModule = annotation.toModule,
                toPack = packName,
                toClass = className,
                instanceType = INSTANCE_TYPE_PROVIDER,
                scope = annotation.atScope,
                tag = annotation.toTag
        )
    }

    private fun CodeBlock.Builder.bindInstanceElement(element: Element) {
        val packageName = processingEnv.elementUtils.getPackageOf(element).toString()
        val className = element.simpleName.toString()
        add("%T(", ClassName(packageName, className))
        val constructor = element.enclosedElements.find { enclosedElement ->
            enclosedElement.kind == ElementKind.CONSTRUCTOR
        } as? ExecutableElement

        var propCount = 0
        constructor?.parameters?.forEach { property ->
            if(addProperty(property, propCount)) {
                propCount++
            }
        }
        if(propCount > 0) add("\n")
        add(")")
    }

    private fun CodeBlock.Builder.bindMethodElement(element: Element) {
        val packageOf = processingEnv.elementUtils.getPackageOf(element).toString()
        val parentElement = processingEnv.typeUtils.asElement(element.enclosingElement.asType())
        val methodName = element.simpleName.toString()
        val parentName = parentElement.simpleName.toString()

        if(packageOf.contains(KODI_ERROR_PACKAGE_NAME)) {
            val error = ERROR_ANNOTATION_WITHOUT_PROPERTY_WITH.format(methodName)
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, error)
            throwKodiException<IllegalArgumentException>(error)
        }

        val isAbstract = element.modifiers.contains(Modifier.ABSTRACT) || parentElement.modifiers.contains(Modifier.ABSTRACT)

        when {
            isAbstract -> {
                addInstance(packName = packageOf, className = parentName)
                add(".$methodName(")
            }
            element.modifiers.contains(Modifier.STATIC) -> {
                add("%M(", MemberName(packageOf, methodName))
            }
            parentElement.modifiers.contains(Modifier.STATIC) -> {
                val parentPackageOf = parentElement.asType().toString()
                val companionElement = processingEnv.typeUtils.asElement(parentElement.enclosingElement.asType())
                val companionName = companionElement.simpleName.toString()
                val companionPackageOf = processingEnv.elementUtils.getPackageOf(element).toString()
                add("%T.%M(", ClassName(companionPackageOf, companionName), MemberName(parentPackageOf, methodName))
            }
            else -> {
                val parentPackageOf = parentElement.asType().toString()
                add("%T.%M(", ClassName(packageOf, parentName), MemberName(parentPackageOf, methodName))
            }
        }

        var propCount = 0
        (element as? ExecutableElement)?.parameters?.forEach { variable ->
            if(addProperty(variable, propCount)) {
                propCount++
            }
        }
        if(propCount > 0) add("\n")
        add(")")
    }

    private fun CodeBlock.Builder.addProperty(property: Element, propCount: Int): Boolean {
        val parameterType = property.asType().asTypeName().toString()
        return if (!parameterType.contains(KODI_ERROR_PACKAGE_NAME) && property.getAnnotation(IgnoreInstance::class.java) == null) {
            val propertyName = property.simpleName.toString()
            if(propCount > 0) {
                add(",")
            }

            add("\n")
            property.getAnnotation(WithInstance::class.java)?.let {
                val tag = it.tag
                val scope = it.scope
                val packageAndClass = property.getAnnotationClassValue<WithInstance> { with }.toString()
                val (packName, className) = packageAndClass.getPackAndClass()
                add("$propertyName = ")
                addInstance(tag, scope, packName, className)
            } ?: apply {
                val propertyElement = processingEnv.typeUtils.asElement(property.asType())
                val propertyPackName = processingEnv.elementUtils.getPackageOf(propertyElement).toString()
                val propertyClassName = propertyElement.simpleName.toString()
                add("$propertyName = ")
                addInstance(packName = propertyPackName, className = propertyClassName)
            }
            true
        } else false
    }

    private fun CodeBlock.Builder.addInstance(
            tag: String = "",
            scope: String = "",
            packName: String = "",
            className: String = ""
    ) {
        val instanceMember = MemberName(KODI_PACKAGE_PATH, KODI_MEMBER_INSTANCE)
        add("%M", instanceMember)
        if(packName.isNotEmpty() && className.isNotEmpty() && !packName.contains(KODI_ERROR_PACKAGE_NAME)) {
            add("<%T>", ClassName(packName, className))
        }

        if(tag.isNotEmpty() && scope.isNotEmpty()) {
            add("($KODI_TAG_PATTERN, $KODI_SCOPE_PATTERN)", instanceMember, tag, scope)
        } else if(tag.isNotEmpty()) {
            add("($KODI_TAG_PATTERN)", instanceMember, tag)
        } else if(scope.isNotEmpty()) {
            add("($KODI_SCOPE_PATTERN)", instanceMember, scope)
        } else {
            add("()")
        }
    }

    private fun collectAnnotationData(
            elementsSet: Set<Element>,
            modulesMap: MutableMap<String, MutableList<KodiBindData>>,
            elementDataHandler: (Element) -> KodiBindData
    ): Boolean {
        elementsSet.forEach { element ->
                    val kind = element.kind
                    if (kind != ElementKind.METHOD && kind != ElementKind.CLASS) {
                        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Only classes and methods can be annotated as @BindSingle or @BindProvider")
                        return true
                    }
                    val bindingData = elementDataHandler(element)
                    val moduleName = bindingData.toModule.takeIf { it.isNotEmpty() } ?: KODI_DEFAULT_MODULE_NAME
                    val list = modulesMap.getOrPut(moduleName) { mutableListOf() }
                    list.add(bindingData)
                }
        return false
    }

    private fun String.getPackAndClass(): Pair<String, String> {
        val splitted = this.split(".")
        val className = splitted.last()
        val packName = splitted.subList(0, splitted.size-1).joinToString(".")
        return packName to className
    }

    private inline fun <reified T : Annotation> Element.getAnnotationClassValue(f: T.() -> KClass<*>) = try {
        getAnnotation(T::class.java).f()
        throw Exception("Expected to get a MirroredTypeException")
    } catch (e: MirroredTypeException) {
        e.typeMirror
    }
}
