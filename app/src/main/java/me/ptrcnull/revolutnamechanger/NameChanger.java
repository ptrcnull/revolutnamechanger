package me.ptrcnull.revolutnamechanger;

import android.annotation.SuppressLint;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XCallback;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

public class NameChanger implements IXposedHookLoadPackage {
    public static XC_MethodHook skipAndPrint(String message) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log(message);
                param.setResult(null);
            }
        };
    }

    private static class RootCheckerBypass extends XC_MethodHook {
        private final Field field;

        private RootCheckerBypass(Field field) {
            this.field = field;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Class<?> actualClass = Objects.requireNonNull(field.get(param.thisObject)).getClass();
            XposedBridge.hookAllMethods(actualClass, "onCreate", skipAndPrint("rootChecker oncreate"));
            XposedBridge.hookAllMethods(actualClass, "onStart", skipAndPrint("rootChecker onstart"));
            XposedBridge.hookAllMethods(actualClass, "onStop", skipAndPrint("rootChecker onstop"));
            XposedBridge.hookAllMethods(actualClass, "onDestroy", skipAndPrint("rootChecker ondestroy"));
        }
    }

    private static Field findActivityField(Field[] fields) throws Exception {
        for (Field field : fields) {
            for (Method method : field.getType().getMethods()) {
                if (method.getName().equals("onStart")) {
                    return field;
                }
            }
        }
        throw new Exception("Could not find root checker activity");
    }

    @SuppressLint("NewApi")
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadedPackage) throws Throwable {
        if (!Objects.equals(loadedPackage.packageName, "com.revolut.revolut")) {
            return;
        }

        XposedBridge.log("awoo");

        // massive heuristics
        final Class<?> loginActivity = XposedHelpers.findClass("com.revolut.ui.login.pin.LoginActivity", loadedPackage.classLoader);
        Field rootCheckerActivityField = findActivityField(loginActivity.getDeclaredFields());
        XposedBridge.hookAllMethods(loginActivity, "onCreate", new RootCheckerBypass(rootCheckerActivityField));

        // even more massive heuristics
        // get Retrofit service
        Class<?> userService = loadedPackage.classLoader.loadClass("com.revolut.feature.user.impl.network.service.UserService");
        // @GET currentUser() returns Single<UserDto>
        Method currentUserMethod = userService.getMethod("currentUser");
        ParameterizedType returnType = (ParameterizedType) currentUserMethod.getGenericReturnType();
        Type userDtoType = returnType.getActualTypeArguments()[0];
        Class<?> userDto = loadedPackage.classLoader.loadClass(userDtoType.getTypeName());
        // UserDto has only one field with type UserInfoDto
        Class<?> userInfoDto = userDto.getDeclaredFields()[0].getType();
        XposedBridge.log(Arrays.toString(userInfoDto.getConstructors()));
        Field firstNameField = null;
        // get field marked with annotation @SerializedName("firstName")
        for (Field dtoField : userInfoDto.getDeclaredFields()) {
            // ugly shit
            if (Arrays.toString(dtoField.getAnnotations()).contains("firstName")) {
                firstNameField = dtoField;
            }
        }
        Field finalFirstNameField = firstNameField;
        finalFirstNameField.setAccessible(true);
        // bind every getter method to overwrite the firstName field
        // with java reflection there's no way to get which field a method is accessing,
        // so this is the best we can settle for, at least for now
        for (Method method : userInfoDto.getDeclaredMethods()) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    finalFirstNameField.set(param.thisObject, "Name");
                }
            });
        }

        XposedBridge.log("initialized");
    }
}
