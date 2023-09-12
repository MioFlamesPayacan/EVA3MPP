package com.mioflames.eva3mpp

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime


//Define las dos pantallas de la aplicación
enum class Pantalla{
    FORM,
    PHOTO
}
//ViewModel principal para la aplicación de cámara
class CamaraAppViewModel: ViewModel(){
    //variable de estado de la pantalla actual
    val pantalla = mutableStateOf(Pantalla.FORM)
    //callbacks para manejar eventos
    var onPermisoCamara     :() -> Unit = {}
    var onPermisoUbicacion  :() -> Unit = {}
    //lanzador de permisos para solicitar el permiso al sistema
    var lanzadorPermisos:ActivityResultLauncher<Array<String>>? = null
    //funcion para cambiar entre pantallas
    fun FormScreen()    {pantalla.value = Pantalla.FORM}
    fun PhotoScreen()   {pantalla.value = Pantalla.PHOTO}
}

//ViewModel para la pantalla del formulario
class FormViewModel: ViewModel(){
    val nombreLugar = mutableStateOf("")
    val latitud     = mutableStateOf(0.0)
    val longitud    = mutableStateOf(0.0)
    val fotoLugar   = mutableStateOf<Uri?>(null)
}

class MainActivity : ComponentActivity() {

    //Declaración de viewmodel para la camara
    val cameraAppVM: CamaraAppViewModel by viewModels()
    //Declaración de controlador de camara
    lateinit var CameraController: LifecycleCameraController
    //Lanzador de permiso
    val lanzadorPermisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
    {
        //Manejo de resputas de solicitud de múltiples permisos
        when {
            (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false) or
            (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false) -> {
                Log.v("callback RequestMultiple", "Permisos de ubicación otorgado")
                cameraAppVM.onPermisoUbicacion()
            }
            (it[android.Manifest.permission.CAMERA] ?: false) ->{
                Log.v("CallBack", "Permisos de cámara entregados")
                cameraAppVM.onPermisoCamara()
            }
            else ->{

            }
        }
    }

    //Funcion para la configuración de la camara, creando una instancia del controlador y
    //vincularlo al ciclo
    private fun configCamara(){
        CameraController = LifecycleCameraController(this)
        CameraController.bindToLifecycle(this)
        //Selecciona la camara trasea como la camara predeterminada al usar la app
        CameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //Asigna al lanzador los permisos del viewmodel
        cameraAppVM.lanzadorPermisos = lanzadorPermisos
        //Inicialización de la configuración de la camara
        configCamara()
        super.onCreate(savedInstanceState)
        setContent {
            AppUI(CameraController)
        }
    }
}


@Composable
fun AppUI(cameraController: CameraController){
    //variable para obtener el contexto actual
    val contexto = LocalContext.current
    //Obtiene instancias de los viewmodels declarados anteriormente
    val formViewModel:FormViewModel         = viewModel()
    val cameraViewModel:CamaraAppViewModel  = viewModel()
    //When para determinar la pantalla actual
    when(cameraViewModel.pantalla.value){
        Pantalla.FORM ->{

            PantallaForm(formViewModel,
                tomarFotoOnClick = {
                    //evento onclick para cambiar la pantalla de visualización
                    cameraViewModel.PhotoScreen()
                    cameraViewModel.lanzadorPermisos?.launch(arrayOf(android.Manifest.permission.CAMERA))
                },
                actualizarUbicacionOnClick = {
                    //configuración del callback para obtener la ubicación cuando se clickee
                    cameraViewModel.onPermisoUbicacion = {
                        getUbicacion(contexto){
                            //actualizacion de los datos de longitud y latitud
                            formViewModel.latitud.value = it.latitude
                            formViewModel.longitud.value = it.longitude
                        }
                    }
                    //solicitud de permisos de ubicación
                    cameraViewModel.lanzadorPermisos?.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            )
        }
        Pantalla.PHOTO ->{
            pantallaFoto(formViewModel, cameraViewModel,cameraController)
        }
        else -> {
            Log.v("AppUI()", "")
        }
    }
}

@Composable
fun PantallaForm
            (formVM: FormViewModel,
             tomarFotoOnClick:()-> Unit = {},
             actualizarUbicacionOnClick:() -> Unit = {})
{
    //variable contexto local
    val contexto = LocalContext.current
    //Contenedor
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        //campo de texto para ingresar el nombre del lugar visitado
        TextField(
            label = { Text(text = "Ingrese el nombre del lugar visitado") },
            value = formVM.nombreLugar.value,
            onValueChange = {formVM.nombreLugar.value = it},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp))
        Text(text = "Fotografía del lugar")
        //Boton con el evento para tomar la fotografía
        Button(
            onClick = { tomarFotoOnClick()}) {
            Text(text = "Tomar fotografía")
        }
        //Muestra la fotografía si está disponible en el viewmodel
        formVM.fotoLugar.value?.also {
            Box(Modifier.size(200.dp, 100.dp)){
                Image(
                    modifier = Modifier.clickable {formVM.fotoLugar.value},
                    painter = BitmapPainter(uri2imageBitmap(it, contexto)),
                    contentDescription = "Imagen del lugar visitado ${formVM.nombreLugar.value}"
                )
            }
        }


        Text("La ubicación es: latitud: ${formVM.latitud.value} y " +
                "longitud: ${formVM.longitud.value}")
        //Actualización de la ubicacion
        Button(onClick = {actualizarUbicacionOnClick()}) {
            Text("Actualizar ubicación")
        }
        Spacer(modifier = Modifier.height(100.dp))
        //Muestra el componente de mapa con la latitud y longitud
        MapaOsmUI(formVM.latitud.value, formVM.longitud.value)
    }
}

@Composable
fun pantallaFoto(
    formVM: FormViewModel,
    appViewModel: CamaraAppViewModel,
    cameraController: CameraController
){
    val contexto = LocalContext.current
    //Vista de la camara utilizando androidview
    AndroidView(
        //crea una vista previa de la camara
        factory = {
            PreviewView(it).apply{controller = cameraController}
        },
        modifier = Modifier.fillMaxSize())
    //Botón para capturar la foto utilizando la función
    Button(
        onClick = {
        capturarFoto(
            cameraController,
            // llamado a la función quecrea un archivo privado para almacenar
            crearArchivoImagenPrivado(contexto),
            contexto){
            formVM.fotoLugar.value = it
            appViewModel.FormScreen()
        }
        }) {
        Text("Tomar foto")
    }
}

@Composable
fun MapaOsmUI(latitud: Double, longitud:Double){
    val contexto = LocalContext.current

    AndroidView(
        factory ={
            MapView(it).also {
                it.setTileSource(TileSourceFactory.MAPNIK)
                Configuration.getInstance().userAgentValue =
                    contexto.packageName
            }
        }, update = {
            it.overlays.removeIf { true }
            it.invalidate()
            it.controller.setZoom(18.0)
            val geoPoint = GeoPoint(latitud, longitud)
            it.controller.animateTo(geoPoint)

            val marcador = Marker(it)
            marcador.position = geoPoint
            marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.overlays.add(marcador)
        } )
}

fun generarNombreSegunFecha() :String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0,14)

fun uri2imageBitmap(uri:Uri, contexto: Context) = BitmapFactory.decodeStream(
    contexto.contentResolver.openInputStream(uri)
).asImageBitmap()

fun crearArchivoImagenPrivado(contexto: Context):File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombreSegunFecha()}.jpg"
)

fun capturarFoto(
    cameraController: CameraController,
    archivo : File,
    contexto: Context,
    onImagenGuardada: (uri: Uri) -> Unit
){
    val opciones = OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object: OnImageSavedCallback{
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let{
                    onImagenGuardada(it)
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("capturarFoto::OnImageSavedCallback::onError", exception.message?:
                "Error")
            }
        }
    )
}

class sinPermisoException(mensaje:String) : Exception(mensaje)

fun getUbicacion(contexto: Context, onUbicacion:(location: Location) -> Unit): Unit {
    try {
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)
        val tarea = servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener(){
            onUbicacion(it)
        }
    } catch (e:SecurityException){
        throw sinPermisoException(e.message?: "No tiene permisos para obtener la ubicacion")
    }
}





