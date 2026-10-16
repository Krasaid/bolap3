# HiFi Player (Android)

## Características Principales

*   **Reproductor Moderno (Media3 / ExoPlayer):** Reproducción de audio estable y eficiente utilizando la última librería de medios de Android (Media3).
*   **Biblioteca Inteligente y Persistente:** Escaneo rápido del dispositivo usando **MediaStore** y **Storage Access Framework (SAF)** para seleccionar carpetas manualmente. Los datos se guardan localmente en una base de datos **Room** para un inicio instantáneo.
*   **Gestión Estricta de Archivos:** Filtra automáticamente carpetas con archivos `.nomedia` y permite al usuario excluir directorios específicos (ideal para ocultar notas de voz de WhatsApp o sonidos de juegos).
*   **Controles por Gestos:** Controla la música interactuando directamente con la carátula del álbum en el reproductor (Doble toque para Play/Pause, Deslizar lateralmente para cambiar de canción, Deslizar hacia abajo para minimizar).
*   **Mini Reproductor Global:** Un mini reproductor que acompaña al usuario por toda la aplicación, completamente sincronizado con el reproductor principal.
*   **Extracción de Metadatos y Carátulas:** Utiliza `MediaMetadataRetriever` con una caché eficiente (`LruCache`) para cargar las carátulas nativas incrustadas en los archivos de audio.
*   **Integración Nativa:** 
    *   Manejo de Foco de Audio (pausa la música si abres un video o recibes una llamada).
    *   Soporte para el Ecualizador nativo del dispositivo.
    *   Temporizador de apagado (Sleep Timer).
    *   Eliminación segura de archivos físicos (soporte para Android 11+ `MediaStore.createDeleteRequest`).
*   **Preparado para la Nube (Firebase):** Configurado con Firebase Auth (Autenticación Anónima) y Firestore con persistencia offline, sentando la base para guardar playlists y preferencias en la nube de forma transparente y privada.

## Stack Tecnológico

*   **Lenguaje:** Java
*   **UI:** Vistas clásicas (XML), Material Design (BottomSheetDialogs), Íconos Vectoriales.
*   **Arquitectura Multimedia:** `androidx.media3:media3-exoplayer` y `androidx.media3:media3-common`.
*   **Base de Datos Local:** Room Database (`androidx.room`).
*   **Backend & Nube:** Firebase BoM, Firebase Auth, Firebase Firestore.
*   **Patrones:** Patrón Singleton para el ciclo de vida del reproductor (`PlayerManager`), Controladores intermedios (`PlaybackController`), Separación de Responsabilidades (Capa de datos vs UI).

## Estructura del Proyecto

*   `adapter/`: Adaptadores para los RecyclerViews (`SongAdapter`, `GroupAdapter`).
*   `audio/`: Clases responsables de la reproducción (`PlayerManager`, `PlaybackController`, `AudioFocusManager`).
*   `auth/`: Gestión de inicio de sesión anónimo con Firebase (`FirebaseAuthManager`).
*   `data/`: Repositorios y preferencias.
    *   `db/`: Entidades y DAOs de Room (`AppDatabase`, `SongEntity`, `SongDao`).
*   `media/`: Lógica de extracción de metadatos (`AlbumArtLoader`).
*   `model/`: Modelos de datos (`Song`, `GroupItem`).
*   `...Activity.java`: Vistas principales (Bienvenida, Escaneo, Biblioteca, Reproductor, Estados vacíos/errores).

## Requisitos e Instalación

1.  **Requisitos mínimos:**
    *   Android Studio (Iguana o superior recomendado).
    *   Min SDK: 34 (Android 14).
    *   Target SDK: 37.
2.  **Configuración de Firebase:**
    *   Asegúrate de que el archivo `google-services.json` esté en la ruta `app/google-services.json` (actualmente configurado).
3.  **Compilación:**
    *   Clona o abre el proyecto en Android Studio.
    *   Sincroniza el proyecto con Gradle.
    *   Ejecuta en tu dispositivo o emulador (Build > Run `app`).

## Próximos Pasos (Roadmap)
*   [ ] Implementar **Foreground Service** y MediaSession para mostrar la notificación de reproducción interactiva en la barra de estado y pantalla de bloqueo.
*   [ ] Pantalla de detalles del artista / álbum.
*   [ ] Creación y gestión de Playlists (apoyado en Firebase Firestore).
*   [ ] Soporte para letras de canciones (LRC).

---
*Desarrollado con ♥ para amantes de la música de alta fidelidad.*
