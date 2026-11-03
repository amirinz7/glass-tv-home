package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dynamic 4K dark desktop wallpaper manager.
 * Fetches high-resolution (3840x2160) atmospheric and moody desktop wallpapers
 * directly from an online feed or a curated Unsplash 4K catalog.
 * Caches wallpapers locally for instant loading and offline resilience.
 */
final class OnlineWallpapers {
    private static final String TAG = "TvHome.Online";
    static final String DIR = "online-wallpapers";
    private static final String PREFS_NAME = "online_wallpapers";
    private static final String PREF_LAST_SYNC = "last_sync_time";
    private static final long REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000; // 6 hours
    private static final int MAX_CACHE_FILES = 150;
    private static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024; // 25 MB limit per 4K/8K image

    static class WallpaperItem {
        final String url;
        final String title;
        final String description;
        final String category;

        WallpaperItem(String url, String title, String description, String category) {
            this.url = url;
            this.title = title;
            this.description = description;
            this.category = category;
        }
    }

    // Built-in curated catalog of dark, moody, atmospheric 4K desktop wallpapers (Unsplash)
    private static final WallpaperItem[] CURATED_CATALOG = new WallpaperItem[]{
            // World journeys: Kyoto, Iceland, Tokyo at night and the Sahara (Pexels)
            new WallpaperItem("https://images.pexels.com/photos/31252768/pexels-photo-31252768.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Kiyomizu-dera, Kyoto", "The wooden temple stage against a mountain backdrop", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/30094291/pexels-photo-30094291.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Japanese Temple in Autumn", "Red and gold maples around a quiet temple", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/30588458/pexels-photo-30588458.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Kinkaku-ji, the Golden Pavilion", "Kyoto's golden temple in full autumn colour", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/35333677/pexels-photo-35333677.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Kyoto Sunset", "Evening light over the city from Kiyomizu-dera", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/17474931/pexels-photo-17474931.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Waterfall in Iceland", "Water falling through green volcanic cliffs", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/19267246/pexels-photo-19267246.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Seljalandsfoss", "The Icelandic waterfall you can walk behind", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/39103198/pexels-photo-39103198.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Kirkjufell and Waterfall", "Iceland's arrow-shaped mountain above a stream of falls", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/29018995/pexels-photo-29018995.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Skógafoss", "A wide curtain of water on Iceland's south coast", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/32670989/pexels-photo-32670989.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Tokyo Neon Street", "Signs glowing along a busy Tokyo street at night", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/31405878/pexels-photo-31405878.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Neon-lit Tokyo", "Narrow Tokyo street lit in pink and blue", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/3532924/pexels-photo-3532924.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Tokyo After Dark", "Street lights and neon down a Tokyo road", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/35549136/pexels-photo-35549136.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Tokyo Night Street", "A lively night street in Japan's capital", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/28829633/pexels-photo-28829633.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Sahara Dunes at Sunset", "Wind-carved dunes turning gold at the end of the day", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/30710179/pexels-photo-30710179.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Sahara at Dusk", "Long shadows over the desert as the light fades", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/30710177/pexels-photo-30710177.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Golden Dunes of Morocco", "Smooth gold sand under a warm sunset sky", "World_Journeys"),
            new WallpaperItem("https://images.pexels.com/photos/30461831/pexels-photo-30461831.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Stars over the Dunes", "A night sky packed with stars above quiet desert sand", "World_Journeys"),
            // Malaysia: Tempat Menarik di Malaysia (16 Koleksi Pexels 4K Ultra HD)
            new WallpaperItem("https://images.pexels.com/photos/40168956/pexels-photo-40168956.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Bornean Orangutan", "An icon of the Borneo rainforest, high in the tropical canopy", "Malaysia_Sabah"),
            new WallpaperItem("https://images.pexels.com/photos/36767161/pexels-photo-36767161.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Kuala Lumpur at Dusk", "The capital's skyline silhouetted against a red and orange sunset", "Malaysia_WP"),
            new WallpaperItem("https://images.pexels.com/photos/338416/pexels-photo-338416.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Putra Mosque and Putrajaya Lake", "The pink dome of Masjid Putra mirrored in the still lake", "Malaysia_Putrajaya"),
            new WallpaperItem("https://images.pexels.com/photos/33676568/pexels-photo-33676568.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Petronas Towers Above the Clouds", "The twin towers rising through a sea of golden cloud over Kuala Lumpur", "Malaysia_WP"),
            new WallpaperItem("https://images.pexels.com/photos/39690291/pexels-photo-39690291.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Melaka Straits Mosque", "The floating mosque on the Straits of Melaka, lit up under a copper night sky", "Malaysia_Melaka"),
            new WallpaperItem("https://images.pexels.com/photos/33708542/pexels-photo-33708542.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Kundasang Mountain Chalet", "A hillside chalet among green slopes in Sabah's cool highlands", "Malaysia_Sabah"),
            new WallpaperItem("https://images.pexels.com/photos/14776919/pexels-photo-14776919.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Titiwangsa Winding Road", "A road twisting over green ridges wrapped in dawn mist", "Malaysia_Pahang"),
            new WallpaperItem("https://images.pexels.com/photos/5224545/pexels-photo-5224545.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Pahang River at First Light", "Morning gold falling on a quiet river and a village on the hillside", "Malaysia_Pahang"),
            new WallpaperItem("https://images.pexels.com/photos/11643318/pexels-photo-11643318.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Red Dusk over the River Valley", "A sunset painting copper-orange along the river bends and hills", "Malaysia_Pahang"),
            new WallpaperItem("https://images.pexels.com/photos/28727775/pexels-photo-28727775.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Tanjung Balau Shore", "Clear blue water and rocky headlands at Desaru facing the South China Sea", "Malaysia_Johor"),
            new WallpaperItem("https://images.pexels.com/photos/38188372/pexels-photo-38188372.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Terengganu Drawbridge", "Kuala Terengganu's landmark drawbridge in the glow of sunset", "Malaysia_Terengganu"),
            new WallpaperItem("https://images.pexels.com/photos/13890758/pexels-photo-13890758.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Royal Town of Kuala Kangsar", "A heritage town on the Perak River, wrapped in tropical green", "Malaysia_Perak"),
            new WallpaperItem("https://images.pexels.com/photos/139974/pexels-photo-139974.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Fishing Boats at Dusk", "Traditional fishing boats resting at a river mouth under a pink evening sky", "Malaysia_Terengganu"),
            new WallpaperItem("https://images.pexels.com/photos/5649468/pexels-photo-5649468.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Shah Alam State Mosque", "The blue dome and four minarets of Sultan Salahuddin Abdul Aziz Shah Mosque at night", "Malaysia_Selangor"),
            new WallpaperItem("https://images.pexels.com/photos/130576/pexels-photo-130576.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Cameron Highlands Tea Hills", "Rolling green tea slopes under a clear blue highland sky", "Malaysia_Pahang"),
            new WallpaperItem("https://images.pexels.com/photos/35905094/pexels-photo-35905094.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Melaka Trishaw", "A trishaw decked with flowers and music rolling through Melaka's heritage lanes", "Malaysia_Melaka"),

            // Nature & Landscapes (Alam Semula Jadi - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Moody Alpine Mountains", "Silhouetted mountain peaks under starry skies", "Landscape"),
            new WallpaperItem("https://images.pexels.com/photos/1366919/pexels-photo-1366919.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Moody Mountain Fog", "Mist rolling through dense mountain evergreen pine forest", "Landscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1531366936337-7c912a4589a7?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Aurora Borealis", "Green atmospheric aurora dancing across arctic skies", "Nature"),
            new WallpaperItem("https://images.pexels.com/photos/1624496/pexels-photo-1624496.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Starry Night Over Pines", "Bright starry cosmos illuminating tall silhouette pine forest", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1448375240586-882707db888b?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Misty Pine Forest", "Dense evergreen canopy cloaked in rolling fog", "Forest"),
            new WallpaperItem("https://images.pexels.com/photos/1287145/pexels-photo-1287145.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Serene Alpine Twilight", "Calm dusk sky casting reflections across quiet mountain lake", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Moody Lake Twilight", "Still dark waters reflecting misty mountain ridges", "Landscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Desert Dunes", "Shadows sweeping over desert dunes under the night sky", "Desert"),
            new WallpaperItem("https://images.pexels.com/photos/3408744/pexels-photo-3408744.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Majestic Snow Mountain Peak", "Crisp dramatic snow ridges against deep blue atmosphere", "Mountains"),
            new WallpaperItem("https://images.unsplash.com/photo-1508739773434-c26b3d09e071?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Volcanic Landscape", "Black volcanic terrain and quiet minimalist horizon", "Nature"),

            // Cityscape & Architecture (Pencakar Langit & Bandar - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Tokyo Cyberpunk Alley", "Moody neon reflections in a narrow Tokyo alleyway", "Cityscape"),
            new WallpaperItem("https://images.pexels.com/photos/374870/pexels-photo-374870.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Metropolis Skyline Twilight", "Glowing skyscrapers towering over serene bay water", "Cityscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Neon Rain Street", "Vibrant reflections shimmering on wet asphalt", "Cityscape"),
            new WallpaperItem("https://images.pexels.com/photos/3052361/pexels-photo-3052361.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Modern Glass Skyscraper", "Geometric glass architectural patterns rising into sky", "Architecture"),
            new WallpaperItem("https://images.pexels.com/photos/169647/pexels-photo-169647.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Cyberpunk Neon Night", "Futuristic high-density city streets glowing in vibrant magenta", "Cityscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1494548162494-384bba4ab999?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Foggy Suspension Bridge", "Golden Gate towers emerging through evening fog", "Architecture"),

            // Space & Astronomy (Angkasa & Kosmos - Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Night Earth Orbit", "Planet Earth illuminated at night from outer space", "Space"),
            new WallpaperItem("https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Milky Way Galaxy", "Deep night sky filled with stellar constellations", "Astronomy"),
            new WallpaperItem("https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Deep Space Nebula", "Cosmic interstellar gas clouds and distant stars", "Cosmos"),
            new WallpaperItem("https://images.unsplash.com/photo-1419242902214-272b3f66ee7a?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Cosmic Dark Starfield", "Deep black void speckled with distant galaxies", "Space"),

            // Ocean & Water (Lautan & Pantai - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Deep Underwater Abyss", "Sunlight penetrating into the dark deep ocean", "Underwater"),
            new WallpaperItem("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Midnight Ocean Waves", "Deep blue waves crashing on a tranquil dark shore", "Oceans"),
            new WallpaperItem("https://images.pexels.com/photos/1001682/pexels-photo-1001682.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Deep Ocean Wave Crest", "Powerful sea water curling in turquoise deep shades", "Oceans"),
            new WallpaperItem("https://images.pexels.com/photos/1032650/pexels-photo-1032650.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Tropical Turquoise Deep Water", "Aerial view of calm ocean reef gradient", "Oceans"),
            new WallpaperItem("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Ocean Trench", "Bioluminescent glow illuminating the deep ocean", "Underwater"),
            new WallpaperItem("https://images.pexels.com/photos/1295138/pexels-photo-1295138.jpeg?auto=compress&cs=tinysrgb&w=3840&h=2160",
                    "Dramatic Sea Cliffs", "Dark oceanic cliffs meeting moody breaking waves", "Coast"),

            // Minimalist & Atmospheric (Minimalis Gelap OLED - Unsplash 4K Curated from https://unsplash.com/s/photos/dark-wallpaper)
            new WallpaperItem("https://images.unsplash.com/photo-1484950763426-56b5bf172dbb?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Lone 4x4 Under the Stars", "A four-wheel drive silhouetted beneath a quiet night sky", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1533134486753-c833f0ed4866?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Grey Sand Ripples", "Misty grey sand curves on Karekare beach, New Zealand", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1762417108293-91614140073a?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Black Cylinder Texture", "Dark 3D cylinders in a minimal pattern", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1507752533523-5186b0bc4c43?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Golden Hour Skyscrapers", "Montreal's skyline in warm evening light", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1470813740244-df37b8c1edcb?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Blue Starry Night", "A galaxy and blue night stars above a rocky gorge", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1509023464722-18d996393ca8?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Mount Fuji and a Silent Lake", "Fuji's silhouette over a still Japanese lake in a deep dark sky", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1491466424936-e304919aada7?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Black Sand Dunes", "Smooth black dunes with perfect shadow contrast", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1622737133809-d95047b9e673?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Polygon Geometry", "Monochrome 3D geometry made for OLED screens", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Silk Black Liquid", "Glossy black liquid flowing through complete darkness", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1550684848-fac1c5b4e853?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Minimal Dark Grid", "Modern perspective lines on pure black", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1507499739999-097706ad8914?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Monolith Arch at Night", "Minimal concrete architecture standing in the quiet dark", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Monochrome Mountain Mist", "Thick fog over mountain ranges in dramatic black and white", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Cyber Circuit", "Advanced circuit board in minimal dark light", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1541701494587-cb58502866ab?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Charcoal Pigment Burst", "A fine, richly textured burst of black dust", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Classic Charcoal Canvas", "Dark oil-paint texture in charcoal tones", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Earth at Night in Black Orbit", "The blue edge of Earth against the black of space", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1519751138087-5bf79df62d5b?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Monstera Leaf Silhouette", "Dark tropical leaves against mysterious black shadow", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1511447333015-45b65e60f6d5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Light Prism", "Faint neon spectrum in total darkness", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Pure OLED Black", "A charcoal-black surface made to save OLED pixels", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1508739773434-c26b3d09e071?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Black Basalt Desert", "Black volcanic earth with a calm, dim horizon", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1491557345352-5929e343eb89?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Obsidian Mountain Peak", "Sharp rock summits piercing dramatic black storm clouds", "Minimalist_OLED")
    };

    private OnlineWallpapers() {
    }

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        return base != null ? new File(base, DIR) : null;
    }

    /** Returns all valid downloaded 4K wallpapers in sorted order. */
    static List<File> list(Context ctx) {
        List<File> out = new ArrayList<>();
        File d = dir(ctx);
        File[] files = d != null ? d.listFiles() : null;
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && f.length() > 0) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                    out.add(f);
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    public static boolean matchesCategory(String itemCat, String selectedCat) {
        if (selectedCat == null || selectedCat.isEmpty() || "All".equalsIgnoreCase(selectedCat)) return true;
        if (itemCat == null) return false;
        String ic = itemCat.toLowerCase(Locale.ROOT);
        String sc = selectedCat.toLowerCase(Locale.ROOT);
        if (sc.startsWith("malaysia_")) {
            String state = sc.substring("malaysia_".length());
            return ic.contains(state);
        }
        if (sc.equals("malaysia") || sc.equals("malaysia_all")) {
            return ic.contains("malay");
        }
        if (sc.contains("sky") || sc.contains("arch") || sc.contains("city")) {
            return ic.contains("sky") || ic.contains("arch") || ic.contains("city") || ic.contains("urban");
        }
        if (sc.contains("nat") || sc.contains("land")) {
            return ic.contains("nat") || ic.contains("land") || ic.contains("mount") || ic.contains("forest") || ic.contains("desert");
        }
        if (sc.contains("space") || sc.contains("cosmo") || sc.contains("astro")) {
            return ic.contains("space") || ic.contains("astro") || ic.contains("cosmo");
        }
        if (sc.contains("ocean") || sc.contains("water") || sc.contains("coast")) {
            return ic.contains("ocean") || ic.contains("underwater") || ic.contains("coast");
        }
        if (sc.contains("minim") || sc.contains("oled") || sc.contains("dark")) {
            return ic.contains("minim") || ic.contains("oled") || ic.contains("dark") || ic.contains("atmos") || ic.contains("weather");
        }
        if (sc.contains("malay")) {
            return ic.contains("malay");
        }
        return ic.contains(sc);
    }

    static void cleanOutdatedCategoryWallpapers(File d, String category) {
        if (d == null || !d.exists()) return;
        Set<String> validHashes = new HashSet<>();
        for (WallpaperItem item : CURATED_CATALOG) {
            if (matchesCategory(item.category, category)) {
                validHashes.add(hashFilename(item.url));
            }
        }
        File[] files = d.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        for (File f : files) {
            String base = f.getName().replace(".jpg", "");
            if (!validHashes.contains(base)) {
                String[] desc = describe(f);
                if (desc != null && desc.length >= 3 && matchesCategory(desc[2], category)) {
                    f.delete();
                    File txt = new File(d, base + ".txt");
                    if (txt.exists()) txt.delete();
                    Log.i(TAG, "Removed outdated " + category + " wallpaper: " + f.getName());
                }
            }
        }
    }

    static List<File> listByCategory(Context ctx, String category) {
        File d = dir(ctx);
        if (d != null && d.exists()) {
            cleanOutdatedCategoryWallpapers(d, category);
        }
        List<File> all = list(ctx);
        if (category == null || category.equalsIgnoreCase("All")) return all;
        List<File> filtered = new ArrayList<>();
        for (File f : all) {
            String[] desc = describe(f);
            if (desc != null && desc.length >= 3) {
                if (matchesCategory(desc[2], category)) {
                    filtered.add(f);
                }
            }
        }
        return filtered;
    }

    static boolean syncCategory(Context ctx, String category) {
        File d = dir(ctx);
        if (d == null) return false;
        if (!d.exists() && !d.mkdirs()) return false;

        cleanOutdatedCategoryWallpapers(d, category);

        List<WallpaperItem> candidates = new ArrayList<>();
        for (WallpaperItem item : CURATED_CATALOG) {
            if (matchesCategory(item.category, category)) {
                candidates.add(item);
            }
        }
        if (candidates.isEmpty()) return false;

        boolean anyDownloaded = false;
        int targetNew = candidates.size();
        int count = 0;

        for (WallpaperItem item : candidates) {
            if (count >= targetNew) break;
            String fileName = hashFilename(item.url) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue;
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(item.url, tempFile)) {
                    String meta = (item.title != null ? item.title : "4K Wallpaper") + "\n"
                            + (item.description != null ? item.description : "") + "\n"
                            + (item.category != null ? item.category : category);
                    try (OutputStream os = new FileOutputStream(tempTxt)) {
                        os.write(meta.getBytes(StandardCharsets.UTF_8));
                    }
                    File finalTxt = new File(d, fileName.replace(".jpg", ".txt"));
                    if (tempFile.renameTo(targetFile)) {
                        tempTxt.renameTo(finalTxt);
                        anyDownloaded = true;
                        count++;
                        Log.i(TAG, "Saved category wallpaper [" + category + "]: " + item.title);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed downloading " + item.url + ": " + e);
            } finally {
                if (tempFile.exists()) tempFile.delete();
                if (tempTxt.exists()) tempTxt.delete();
            }
        }
        return anyDownloaded;
    }

    /** Returns true if sync is needed (empty local pool or refresh interval elapsed). */
    static boolean due(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long last = sp.getLong(PREF_LAST_SYNC, 0);
        return list(ctx).isEmpty() || (System.currentTimeMillis() - last >= REFRESH_INTERVAL_MS);
    }

    /**
     * Reads companion .txt file (same name as picture with .txt extension).
     * Line 1: Title
     * Line 2: Description / Overview
     * Line 3: Category / Source
     */
    static String[] describe(File picture) {
        if (picture == null) return null;
        String name = picture.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return null;
        File text = new File(picture.getParentFile(), name.substring(0, dot) + ".txt");
        if (!text.isFile() || text.length() == 0 || text.length() > 8192) return null;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(text), StandardCharsets.UTF_8))) {
            String title = r.readLine();
            if (title == null || title.trim().isEmpty()) return null;
            String desc = r.readLine();
            String cat = r.readLine();
            return new String[]{
                    title.trim(),
                    desc == null ? "" : desc.trim(),
                    cat == null ? "" : cat.trim()
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Synchronizes wallpapers from an online feed or the built-in curated catalog.
     * Downloads on the caller's thread; call from a background thread.
     * Returns true if one or more fresh pictures were added to the local pool.
     */
    static boolean sync(Context ctx, String feedUrl) {
        File d = dir(ctx);
        if (d == null) return false;
        if (!d.exists() && !d.mkdirs()) return false;

        List<WallpaperItem> items = new ArrayList<>();
        if (feedUrl != null && !feedUrl.trim().isEmpty()) {
            List<WallpaperItem> remote = fetchFeed(feedUrl.trim());
            if (remote != null && !remote.isEmpty()) {
                items.addAll(remote);
                Log.i(TAG, "Loaded " + items.size() + " wallpapers from remote feed: " + feedUrl);
            }
        }

        // If no remote items, use the curated catalog
        if (items.isEmpty()) {
            Collections.addAll(items, CURATED_CATALOG);
        }

        // Shuffle candidate pool so rotation changes over time
        List<WallpaperItem> pool = new ArrayList<>(items);
        Collections.shuffle(pool);

        boolean anyDownloaded = false;
        int targetNew = 5; // Download up to 5 fresh pictures per sync
        int downloadedCount = 0;

        for (WallpaperItem item : pool) {
            if (downloadedCount >= targetNew) break;
            String fileName = hashFilename(item.url) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue; // Already cached
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(item.url, tempFile)) {
                    // Write metadata
                    String meta = (item.title != null ? item.title : "4K Wallpaper") + "\n"
                            + (item.description != null ? item.description : "") + "\n"
                            + (item.category != null ? item.category : "Desktop");
                    try (OutputStream os = new FileOutputStream(tempTxt)) {
                        os.write(meta.getBytes(StandardCharsets.UTF_8));
                    }

                    File finalTxt = new File(d, fileName.replace(".jpg", ".txt"));
                    if (tempFile.renameTo(targetFile)) {
                        tempTxt.renameTo(finalTxt);
                        anyDownloaded = true;
                        downloadedCount++;
                        Log.i(TAG, "Saved 4K wallpaper: " + item.title + " (" + targetFile.length() + " bytes)");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed downloading " + item.url + ": " + e);
            } finally {
                if (tempFile.exists()) tempFile.delete();
                if (tempTxt.exists()) tempTxt.delete();
            }
        }

        // Prune old files if cache exceeds maximum allowed files
        pruneCache(d);

        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                .apply();

        return anyDownloaded;
    }

    private static String hashFilename(String url) {
        return "online_" + Math.abs(url.hashCode());
    }

    private static boolean downloadUrlToFile(String strUrl, File dest) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            URL url = new URL(strUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 GlassTV/2.4 (https://github.com/aqmangge/glass-tv-home)");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + code + " for " + strUrl);
                return false;
            }

            in = conn.getInputStream();
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    Log.w(TAG, "Image exceeds byte limit, aborting: " + strUrl);
                    return false;
                }
                out.write(buf, 0, read);
            }
            out.flush();
            return total > 10_000;
        } catch (Exception e) {
            Log.w(TAG, "Download error: " + e.getMessage());
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static List<WallpaperItem> fetchFeed(String feedUrl) {
        List<WallpaperItem> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(feedUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(10_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (GlassTV; Android)");
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return list;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String raw = sb.toString().trim();
            if (raw.startsWith("[")) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.get(i);
                    if (o instanceof String) {
                        list.add(new WallpaperItem((String) o, "Online Wallpaper", "", "Web Server"));
                    } else if (o instanceof JSONObject) {
                        JSONObject obj = (JSONObject) o;
                        String u = obj.optString("url");
                        if (!u.isEmpty()) {
                            list.add(new WallpaperItem(
                                    u,
                                    obj.optString("title", "Online Wallpaper"),
                                    obj.optString("description", ""),
                                    obj.optString("category", "Web Server")));
                        }
                    }
                }
            } else if (raw.startsWith("{")) {
                JSONObject obj = new JSONObject(raw);
                JSONArray arr = obj.optJSONArray("wallpapers");
                if (arr == null) arr = obj.optJSONArray("images");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        Object o = arr.get(i);
                        if (o instanceof String) {
                            list.add(new WallpaperItem((String) o, "Online Wallpaper", "", "Web Server"));
                        } else if (o instanceof JSONObject) {
                            JSONObject item = (JSONObject) o;
                            String u = item.optString("url");
                            if (!u.isEmpty()) {
                                list.add(new WallpaperItem(
                                        u,
                                        item.optString("title", "Online Wallpaper"),
                                        item.optString("description", ""),
                                        item.optString("category", "Web Server")));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Feed parse error: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    private static void pruneCache(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        if (files == null || files.length <= MAX_CACHE_FILES) return;

        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        // Sort oldest first
        Collections.sort(list, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        int toRemove = list.size() - MAX_CACHE_FILES;
        for (int i = 0; i < toRemove; i++) {
            File f = list.get(i);
            File txt = new File(dir, f.getName().replace(".jpg", ".txt"));
            f.delete();
            if (txt.exists()) txt.delete();
        }
    }
}
