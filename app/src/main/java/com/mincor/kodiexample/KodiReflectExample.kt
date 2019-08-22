package com.mincor.kodiexample

import com.mincor.kodi.core.Kodi
import com.mincor.kodi.core.initKODI
import com.mincor.kodiexample.instances.Post
import com.mincor.kodiexample.single.UserData
import com.mincor.kodireflect.*
import java.util.*

const val TAG_FUN_WITH_PARAMS = "fun_with_params"
const val TAG_FUN_WITHOUT_PARAMS = "fun_without_params"
const val TAG_FUN_FOR_INIT = "fun_for_init"

private const val INSTANCE_TAG = "simple_tag"

const val MY_GLOBAL_CONST = "global_const"

fun main(args: Array<String>) {

    val kodi = initKODI {
        single<UserData>(UUID.randomUUID().toString(), "Aleksandr", "sphc@yandex.ru")

        bind<IUserData, UserDataInstance>()

        constant(MY_GLOBAL_CONST, "hello world from KODI!!!")

        provider(TAG_FUN_FOR_INIT, ::checkInstanceWithTag)

        ///-------- LAZY MUTABLE SECTION
        val testMutableLazyInstance:UserDataInstance? by instanceMutableLazy(instance<UserData>(UUID.randomUUID().toString(), "Alex", "id347435"))

        ///-------- LAZY VAL SECTION ----////
        val singleInstance: UserData by singleLazy(UUID.randomUUID().toString(), "Aleksandr", "sphc@yandex.ru")
        val providerWithReturnAndParams by providerLazy("", ::funcForProviderLazy, UUID.randomUUID().toString())
        val userDataInstance:IUserData by singleLazy()
        val userDataImplemented:UserDataImplemented by instanceLazy()


        println("-----> SINGLE INSTANCE '${singleInstance.name}'")
        val posts = mutableListOf<Post>()
        (0..25).forEach {
            // Only instances with 'var' constructor params can be override by input params if another case check
            posts.add(instance(UUID.randomUUID().toString(), single<UserData>(listOf("1", "2", "3", "4", "5", "6")), "Title $it", "Desc $it"))
        }

        println("-----> INTERFACE BINDING TEST DATA ${userDataInstance.getData()}")
        println("-----> INSTANCE BY LAZY ${userDataImplemented.getData()}")
        println("-----> CONSTANTS TEST  ${constant<String>(MY_GLOBAL_CONST)}")
        println("-----> MutableLazy TEST  ${testMutableLazyInstance?.getData()}")

        val providerData = providerWithReturnAndParams.call()!!
        println("-----> PROVIDER LAZY DATA '${providerData.name}' and email = '${providerData.email}'")

        // val instanceWithTag = instanceByTag<Post>(INSTANCE_TAG, UUID.randomUUID().toString(), singleInstance, "Title with tag", "Desc with tag")
        // println("-----> INSTANCE WITH TAG TITLE '${instanceWithTag.title}'")

        // call the function without params
        providerCall(TAG_FUN_WITHOUT_PARAMS, ::funcWithoutParams)
        // call the function with params
        providerCall(TAG_FUN_WITH_PARAMS, ::funcWithParams, posts)

        /**
         * If you do not set the incoming function parameters it throw with RuntimeException
         */
        // check for singleInstance user
        val singleUser = single<UserData>()
        val providerWithReturns = providerCall("fun_with_return", ::funcWithReturnAndParams, singleUser)
        println("-----> RETURN FROM FUNC '$providerWithReturns'")

        /**
         * Call the function that we bind early in initKODI section
         */
        providerCall<Unit>(TAG_FUN_FOR_INIT)

        /**
         * Call already bounded function with new params
         */
        providerCallByTag<String>(TAG_FUN_WITH_PARAMS, single<UserData>())

        //call provider with RuntimeException, cause there is no providing function gives
        //providerCallByTag<String>("provider_call_without nothing", "HEHE")
        println("-----> END OF PRINTING")
    }
}

fun checkInstanceWithTag() {
    println("-----> HELLO instance with tag and desc = '${Kodi.instanceByTag<Post>(INSTANCE_TAG).desc}'")
}

fun funcForProviderLazy(id:String): UserData {
    return Kodi.instance(id, "Vasya", "vasiliy@gmail.ru")
}

fun funcWithoutParams() {
    println("-----> IM WITHOUT PARAMS")
}

fun funcWithParams(posts:Any) {
    println("-----> IM WITH PARAMS '$posts'")
}

fun funcWithReturnAndParams(user: UserData):String = user.id

interface IUserData {
    fun getData():String
}

open class UserDataInstance(private val userData: UserData = Kodi.single()) : IUserData {
    override fun getData():String {
        return "HELLO WORLD from $userData"
    }
}

class UserDataImplemented : UserDataInstance(Kodi.single()) {
    override fun getData(): String {
        return "HELLO FROM UserDataImplemented NICE TO MEET YOU"
    }
}