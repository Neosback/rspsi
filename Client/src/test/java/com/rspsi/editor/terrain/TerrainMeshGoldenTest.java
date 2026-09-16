package com.rspsi.editor.terrain;

import com.rspsi.editor.model.TileSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks the complete RSPSi-owned topology matrix until external parity replaces it. */
class TerrainMeshGoldenTest {
    private static final String[] GOLDENS = {
            "2cec5cd9bc1d452dab9b8f76b0703df936af40f77ba37fc31ecbaf28d6db670b",
            "c5121f15c5f905beafa18ab0c3565fa533574fa30b80fe2f4f05cf1b3714a66c",
            "57a94678253e41a891813e844dcfb9159dc300a947fd7e2d6ac4f4ab25a22d1f",
            "b13367760914b454e4443678c2c15ff6692bf0ae7c6fcb1beb446791a28162cd",
            "81f37e60e65aa90397653926ded5c10c8431156587dd1ad8f563e0fbe3e4520e",
            "e5188ed83d37dff3e2168244876db3725357dfc0c353e6097e58bc84539969e0",
            "7a93e6c4f90fcee10a8a6cb8274761fa7603a3a31a6c9279fa6b5cd0ff294931",
            "2578377ff646f640b9de2063a8f8e7a18fa4a45467047db57d29b29598bedb9e",
            "5558590fb17887fe1956469250be6cf99fba2727df4c7fdf4222becfebc38013",
            "b8f9de848e2144c509a97474eb8d97ddaaf7934669cde7878bf0d2beb849a9f3",
            "c356ae5f913d8075653767fef74959bfd876cc6fffda71a4d9032dad733995c5",
            "5d8499d47c2696932bea5eb6f1c87a756ac49a05334433692ff030936ee1d9a2",
            "d0a1b421fa001c9580e60f62e8b715c75a547dbe096a1c558a116842b7f0c68d",
            "7db771195a81eb3370e63c3bc83c7ecfb8e57adae3893cd690fec2ede37179c7",
            "3f56a241c5dd75b1515c6f78779c7e80e6a878c369cd030a7e0629e41623ea3d",
            "6c94dfdb4a0f94a9b08b0a71e616380b4d59d6a927e54a992915bbded996b167",
            "88f311691053daec0a7a1d414a6b1911afa09078e85a6d22a8c6b9da409feae4",
            "e219af1e95a2d86474413e343d8a287787d42ddc500e19035bf5dfb00fa6233f",
            "9e9b755566466a513b0223190ee61fa1818884cbc5cc46d0bfbd380083e72ace",
            "54de49928875e369817030ce0dac8b4c3883cb18c635ffcb2bf8eda814afea32",
            "7a75afd226831a260b2c0aa488c68b8dfa8d88484ffda0bff96d590f3105a4e1",
            "fa260716b2390af33a45920981563d4ea886790a00c936b789e185de7313d963",
            "3382a9d1569a61f4e78039c456b566fb2315d204eec3f9d26ce0a76aeb97f913",
            "9e114281f223407880969c91f20a33a29777c2eb1c4dd8632cc92e3e77fc7554",
            "bd5acf4d8d7d0c214c4f83d2128d882af5f331829fd95e1ede7cac1785a3c8c5",
            "acf7ffdb4eaf934dbb38abbb996ca8b78e3b5a5f1307d4d098404d01c6830326",
            "6c1cc955db3ce5cc3417c15595322f63fe759c087bee5972b043f007b6ed8830",
            "c9ae3dd2bdc35788244fb5a493af0f2dc3e31d17d6ebb3170e6460870515f6c1",
            "582476edc4572ab5e8712cde56342fcbc14b978729b0a1ffc0de3dce4d7229a3",
            "7df84f4e4e8fe9b57659cd45fe6113c0b862c402422b90b8e01af99fa22742b4",
            "3a23c3068b1f29ae1efbc935bc94ac6d919cf28019e105d7e670d0b931848c7a",
            "3f737e7e5fd8029a29274b71c0b9b409ff048602bdabded94d3a568eac91f9a3",
            "d558c812d630fcea924216693c54d31f9e6002982ac071619b73c863f48428b5",
            "b77577e8facfe72a8d940f93aca1a7dc61ce87980a87c0feb791d8bf3f279798",
            "38a905d76ff1704a10df14de6085b8e1eea63ed7b4fd553e3fa02db4edd31207",
            "7cb579f3736d8bcbc53f5b9b58d24a12d67575c555ee08c5b78c98e8a2a5c8c4",
            "ad3e78f3a1d35df8c3deb1f8f7bcdec97f8f5580265e515e50f24aae9cdc81a1",
            "0ce0099987413685c89f0db65639f2e8fa1b903976d4e90f12acfd7a4ddfc364",
            "6bd4a259ca855f255714d5b30a909f9c27a49bad74e6ae233231df25b4eab917",
            "353b75c735b2a2806dda0a4245958f538aaa10995dcb3ebec5a3eaa3597359db",
            "8fd5ce2bace242167a0c56e005f608f5beab71663617c879aa858fbe287aa51d",
            "15a561555fe2ec31f1cfc21361474d1a2b65c025352e39d59244a2e863d211db",
            "39e433fa3007d480bd3a4d47049a61aa2bfa3ac5f45e8d456bac7bc9213d5e0b",
            "cee3a966fd02ed4fac96c04dee00e672dbf51d06f6bad0a1eb99606dc0caad41",
            "d50bbadcd7202e2637befe45fcb5316d1ca9ba77fbb9fafda8b699feabdd3aaa",
            "ae8b684dc37d90c5d4753cb7d6c261dd80de13a263cc1fc28f605767d41915a5",
            "3e1454e06ff2ec8314bf4a34a5d89d2d58e1c6e94aee62fd13ea1ec3d36f7f6b",
            "2df869248972fd9f1d5838f5a3f57da2122bf36b4ca0d73263042671ea3f1760",
            "9e81ffb4dfdd6e7055d1786f8ff7cebb11f599fbc3a3955f30d82a64250f72a1",
            "c8ce001bfd6984a761941dc3c82c57fb0777ffff6c62482e65ea47cd10fa337b",
            "720a87a902c56edc0f1a5ab7a71baf33e665d27503b2450e3ab4d79b5547696d",
            "75d5b831191df944408c209a740cbbe706b022ba66de5ee6ae1db81c7e573e4d"
    };

    @Test
    void allThirteenShapesAndFourRotationsMatchTheLockedTopologyGoldens() {
        assertEquals(52, GOLDENS.length);
        TerrainMeshBuilder builder = new TerrainMeshBuilder();
        for (int shape = 0; shape < 13; shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                TileSnapshot tile = new TileSnapshot(10, 20, 30, 40, 2, 3, shape, rotation, 0, List.of());
                assertEquals(GOLDENS[shape * 4 + rotation], digest(builder.build(tile)), shape + "/" + rotation);
            }
        }
    }

    private static String digest(TerrainMesh mesh) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((mesh.vertices() + "|" + mesh.faces()).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
