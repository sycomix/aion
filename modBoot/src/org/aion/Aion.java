/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * 
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.p2p.Handler;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.types.*;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.aion.crypto.ECKeyFac.ECKeyType.ED25519;
import static org.aion.crypto.HashUtil.H256Type.BLAKE2B_256;

public class Aion {

    // known block hashes we're always gonna request from the client (105 hashes here, MAX_NUM_OF_BLOCKS=96)
    public static final String[] known_hashes = {"cd9c666385dbf72c84911d4e9398ed74dfeef1cf99539b0bcdd5ac8980f7a9dd",
            "bf982e3f7b69d376cdda888421ae3f58a98b6fb2e3e95cce9930a4ad2af2bc42",
            "64b65af2aae344375c52c5d4bb683956b4fff6f411b780079672849c9a155d06",
            "789da855eb6718e84cdd150b986e494936741baa5c1128b35f20c44de6dbaeeb",
            "607d5413c83762b7f664688119c4bfe60d37cd15e2eba8e84d71e9a64dbe121e",
            "45b5aec019af3b6af79d976e80845994a35a3814af3375987bd219a008dfda9a",
            "86da0d325d2227b3252c0bb030f5ecd3cf325aedd169b69bb3bf8517a36e55a1",
            "c844fff8e712cccd71fe9a281bc4563385286a12c85e5bfbb9992412db19fa9c",
            "18e995a9fb72e87fae352833b8c1c5d0a918de8b79fdeea6c74f2c86ed2cdae0",
            "496a0f28ac1ee8fbddb29627c3ffa17b6f3853e5b40abe8ad99785cbf57598e4",
            "ffdec0f32e200addf8aae7800d5e7016880e3f2e396cfa5d1aa5224415d53e5a",
            "263de6b1ebcd0e423656c720cf684751083a3c1e5ce4bdac7a6364465223221c",
            "369463a2e8770f09f80a4a8441fdb9b951a41fefd7e73f314402850f4424de4a",
            "fc246f773cbc5b5afd2e18271901a3ee6e89ef568e60d1cad0be0b48d129a888",
            "c7bc2a0679d1b78bc4c0beaf5464889d1e33d3144930e88bfedbc09a69ee573d",
            "104f2438a17d9b69a300c22c56aec8a42f1906ec4defada6628a84bdd1aaf22a",
            "5daf6a501724b329f41435bd8556b127a1553be54bc84b041a7cd708604b3773",
            "8436df073f608c57c2fc1c87f10bd7789a3c0aa173f12466587cc86a11d20822",
            "1a6d816ca253c7587ecbe3008d91f84824cb630e3bca45eff375745b0423d823",
            "3bc2e5fe6971983232242afaf0d530f6585363f0bb49127f76cceffc2c6075cc",
            "3ceb9dc3b51b38486d0cd2108576f68435c1985de94157c8b2dc0b21afd75861",
            "a90d25ec409b3730613f635b4b4108514e8debf1c106ca8c6af0084266b3c6b3",
            "ca24c697ca3fe41edc9dafb71f625a8ebe13ea21711a0f685838e9efcc5caa03",
            "937159e41719fc3ffa08a1e3a1a29a624556515e0505b4fcd2553593fc9dbc92",
            "b6ab9e18a7e0eb3d4b0fdf806cf569674651faf89ef7358977572f68a1891fae",
            "9da858e95a2831da206edd2b8b69ebddf57befed7314822d61555bee5c28e5d1",
            "cde7eae853d5d2c4928f14b4a0d590ca4d28229d1203bd2106ed80e4da1f1dee",
            "b281e7f1361f643607add4720fa30e72e358216786801c46f1b283ab48fad418",
            "3fcfa2049588ae3b7b42c5d2453d76baa9e648c0cece805a8b38cae29b627113",
            "60e14a258e13b1a89d786bd95bd065efa2f0b369e478d90c04b2ebb8b9057a86",
            "92df7b50b279cf2502e022732c739b5826c16b08a2f94d3a7a63758d39492d76",
            "16a191e1e880347e8a3514b0cbf5e7c1b2f01028d41017b0f924e05551bd9c2a",
            "2a8e84eb81ce301ce8c2dfe5e2c8874a758a5d04bb26f5f1f56f66328cd6565b",
            "3a81857e9282321a2c9f8ba6ceed4e0674fbe58ce47070a5b4b143c6f8c0ebae",
            "f3189f97631ced6d3590ec367a7c559ae4f753f6881fc38311d17b8919fc46dd",
            "c50310688022b7756792572d48a787cf9994cc9068919efa4594fe7e4eac8756",
            "78d986718b549dec69b02fc001f6f2cededd4de37e1beb8b8097df01b2bf3462",
            "579046357ac13bb8f16c501da685f970460e9a4ab968c4de30bf1791d4f6a624",
            "b99f8136d3c781b657be8d17a62f10163f0500c0fd3ea6e308e52c6829cbc340",
            "a769b4fb7395a73ae1eae115538d52cbd06401da6bfedc254c0425a7e716049f",
            "75b34e6d094f8b9dd34b5bc11e45524ee9f0da682911d4c4e40f253cef83e27b",
            "3276967ef3abbf2335face8376a4e469a984db32b48bbc712c8f152b85e8079f",
            "dba3d6b80eeff8bb2f641c4c51f95168e690931d7f170d44b1a6fb1db360ff94",
            "c1995a8e9aeff44d6495462f8d8440bdd0572adc32135478ed59c47ec0fbf37a",
            "39c57a4c58badc5f31b1cf5b926aa57c77d0fe2ac87e1479d08224cc769f0995",
            "44c8df3a2d9dda54efeebcf772c161e05fd134893453b9c496293565f31b0aea",
            "5647b90d699a7a66888579ed6ac033fb60d6d4fe834f763359c73c43043c4b97",
            "6047eb9c295e31027834b78384be0db0721ac4338c56b719771e9fb12bf66fc1",
            "4f32c381901ef0ef3b7667d0da2e86c169c50ee35cbf06629352c417e029d1a1",
            "8d48f396cba3b2b947f636c468d1009f42e0f0e6f88c1690a5c7c93b805a7ed7",
            "72633f2d461192e01f5444dea7f45a761fe27d01858843aa66b2582d12b0c095",
            "730d75e8caa5451b7cacb2acf842138998b3772c7cf36c221b5b098535d9fdf8",
            "07d0e35d19b61eea3513e861b6c8785b8d81720e4f09f56969936b4d07a11e8d",
            "47ee7ac85d61d993e73408b5b3ee14924163452d8e88f0283c9f259374fe3907",
            "788b800f5ffb8d6724f7b44d067b9ab982df5423bdbac2a3a81248ac65cf21c0",
            "bc4bb89098ff1a28b02536c6ec067468d1a0c29643a0ba6d4111eaf403e9cba0",
            "437683b5b86a90edc1ce82c0d835549c11a39a07a5bb7ea0d10052c636386dde",
            "a309ddae14c99b4113cbb9370df85f5b9a21e99ba877131ab86987ce36a5bc6f",
            "938cc2d3e82964ab2cf469160739e4a08f9ea4a068c06309ff4f63810ca99d47",
            "be71ce5acb965e6d93f3fe35ccc6b73ff3c10fdd6f4e61eb8c0f6b91b97c0e4e",
            "aed63e088ad06eb662cdce2562052bad8c7e6ff3b8a04e6a4b76d19a91708ccd",
            "4a9b2dad73013bea6d24882fe07a2e2df2b37e48336ce6ec141e0e17b745aea0",
            "c7ee2ab83f3050715df53c1fbbe28ef43e7423a83c4a70d980d2894a58dbb9eb",
            "bcfd304170d1ebee56ea9baaa56078e84294ac81cb0d96105bed152d983d2e32",
            "ab9f32e1af3c5176f47bf237b9bef3fc50117c42bd714e9c0159d5cc9fc0508d",
            "351445ec98d00f49fa10ee90a2d724554d8b822e82ca92c1d0432f2822e48ea0",
            "98e004a1386a69f937dd29cc50d08ed83517c347f04f81914d66877bf6e14e23",
            "a3518da0d3be38c92581166a260aa5420d4741a983a33f3bdc70ed4d0b1b0075",
            "bf255b8f469fb948c841f322c2c6a5426ca73bf7e6901ca4ba4a902f5fa63c19",
            "33267125a05acc0fc50d7eff178de6a99fc3cb3f93c72197b105bbcf03e40bdb",
            "1d0a1afcc11129498420394d5eb1157b5083ae4ed1a81c80c14bd9582f781336",
            "5306c1cc426a1f09dcaec23f9d32507735a77433c43ed776671b9e6be0fa0586",
            "178e51369b243a356d0007959eb7968fc45c6c404ca8957aee9702e0a761da5b",
            "8d312e9c0da1ccf8a86b0974b3db7760b927569a0b46265a3f1be1b134be5aa6",
            "d32fa84a7da7878073d83ab92ef0d0133dab6ff1e7e7244e03ca7edb1d7297bf",
            "c84af4ef5a7c2fed4e1b3f95615dc5150a04163f6b3e945e5b40fd7cd3c7fd8d",
            "327d62438a1caaecfc4be70117fc1f58855977b66d3ee46217b5df181e8cfe2c",
            "9ee37d8bde8e1a650e14a77407510040eb330b9e1aa097d5a13f9c8301090c59",
            "7044e32f1de199d86398515dfcc9c5eab6fbcb939b57c781602e844b55b962eb",
            "3512a29af4d08dbd652e2874feef0e2ece2bf99ebefd03faa5fa432109e4927d",
            "06037c31ec7139c76ea94e39bef91f65696bf3b6a5801e7e8705d5c4ab3ff0a6",
            "b23a3e9464fdb35dfb68a6c0e26721dec7fdb93f8e87ee33292fead803a90b92",
            "0e18ab74bf8bf42924cc0acd8f5c37ac6062364dd5fb7bcc13ad725471b1ed7a",
            "cb7808580fcfe4099c7b7520ba8d343fe9ee47bc6918b6e67d3427459ad36cd7",
            "71afffd4e4fe046be9f476753400cd9b8e53384522d985f7438c48399d024cd2",
            "161ba7c9d6b25ab3064932ccf6bd98e369281b43743d508570735a260d4d11cc",
            "c16640ba408c28874499320c0ca8aca684f26a102ae72ad355879beaa7ce3117",
            "4d8a3b1429b2431a7ffea1c5309aeeb20f279fb2d7db05d6f34bb0c4dc15f88c",
            "c62f56d7353be929cd3d6faf27d1b3843a5f455c8c983db0c592fefbbe24fcbc",
            "9c8a0061235b21ea428e81b7c0cb681ebfbb9a8722e8493575516e28f0e6e3b6",
            "2644015cab042682102b1e7bd143bb0ce279ff94238eab78c901651707004048",
            "eb26f8e4bbcfb86a1798a3e2e9a77a6ee8f637489374a9967b90ec25b1514e56",
            "1ec9e776b000d8a8143ec7e8dc47143b1675cb1e6695021a66635761cc6657d5",
            "2b26bd520f4f02a8e276789efe285da8c66c626c515607d69ba2e28f41edb2c4",
            "5d2cdada4b1428f99324af8a21809a173e06b0c92f7d9c4b68ed9485b49edf62",
            "0a59cd067434121543c24aa1d14b9c1518b780820bfeabc5cdfb5976a0de6452",
            "5267a2293ddcfda5203b64a194c8212bd52d7d72c2727c8eb75655df026570da",
            "c37a81b5b2894ca3e750fb6372e5ca0b076eb50bc6d4f7f1941b815107a67ed8",
            "8ca7ade543de8241baf8943f012e8141210837e18526c9448b21745b384681cc",
            "328e12684276058f8f6086b28e2a66dcd5c6187735ede59a3ed83777ff3ba69d"};

    /*
    public static final String[] known_hashes = {"0x95d5140c21e3a060c0456aec5a31d626730b6582001ab55b8eb08412143b6c44",
            "0xee8fe764454d0c9f1f9a608a7db69e976d9ecc6c2ae787879327fb8fd8385fe7",
            "0x9675c0183ebfd341b008fa38c1de0eaa60425670b9f190434c3cfc12f96fac3d",
            "0xbf7c3363a74f3994970599607ee0ba90a8bb2f34c27ac357dfcd01d2da50f35a",
            "0x26ef227c35611a433cc489e9013a3fcdc60c2a37db1d723f440ff37fa77aa058",
            "0x6224fc3e3f6bc72c826c652ad26b589aefae5767b560b14e36b08fe57f037238",
            "0xfeebaa9f3ccba3bb8c7237c6b84d5ef8cecec09fde72d8dae50cbdb30b051596",
            "0x764686eb23b84fabbfbb73c48a55b44610454bec6fbf637abf2f6cb184df83d7",
            "0x5e940d5763bb48a03096b2e8a9362ea9c64afd8c2df3dba8c7370c0d1ed41278",
            "0xbf4ed5cad2269244500fd3aa7404f0e54dceaa7d1e601049f4f6bc7024d218f3",
            "0x6b9e5ff842b12ab341ab7134b386d75d5defa26bc44e374cd3366386178f6504",
            "0xd7eddf4314ff3791bca745528a391f38d15ed616ff37baf7c341a2398e5eace3",
            "0xd3a278c6dd5b7cf21c791fe3359af17bcac16204db2c225496607e1594efbf5a",
            "0x7e904f5f7ae41bf5e99a64f8e2f3e6c928b111afb2b13962f7d93a41ae4be7a5",
            "0x002d0d98af003918cd7a01a780ce574cdcadf83faef81ac94079e22af91d5332",
            "0xea6cc4c8e6340cfe2bc2c81744563d85e72c1a97e8ac315799fb9c5603df1dec",
            "0x60059c1c0c38831d3f4490483a604355e36a48eea570247b0f735ebc44b2f169",
            "0xb68d324317587bf4d1407ebd4b92d666f09e30e39834976921de333b1c448fe1",
            "0x189e1f89373c15368f1a7e24997eef8722aa60e7b1075698c62b3ef616c6a6cf",
            "0x790f260d338e8be65018ddff4009b795135905cb505dc72654c6ced2c6d2d515",
            "0xb3c857c3a0c4a33bb993bfe4e19224821237aa2cf493ef236c324467b855e488",
            "0xdbfb166cd4c267260f4566f35cc55193f549f505c32365a5864649c65913c1b4",
            "0x69d4e4ce1f291d215ba8199d2ae386cd9705bcf065320a9881c8c8ff30569e80",
            "0xc6048834460fa93d7528e3f49c69c676febcc5639d387bc9c07e8530fcd6231c",
            "0xd556fea89cced9fcbbd74be88d557a221e24c8a78eb08c2c5e5caec8ad50426d",
            "0x1ce86cdb13e3f56b316fea41321cae219828b5e902dbbde9ad8cdef119419d35",
            "0x1e5c9645199f1afe45609ff5afd33a362ab557cb2fcb9c8923771eb887a87d06",
            "0x9e70d7613ca51a1b45948ffd75ea8cccefcae8ad6bdf8ef39baeb15ba189a4d6",
            "0x5eae90961567014280f1c838a8c635a0dd36567bfebb6403b57c06e7e24122b5",
            "0xbd61bf39be3a6ff634732cfb9a711fbccb9d23d2eeea79982d0ec632831819c9",
            "0x8867e4be3e4f277ae6a1700be6e3cec8fdfd4d0c7de1a8681a253261f8b7ead8",
            "0x67a8be3e7595043534cd0b2c04284f0d66b6b74681aaf93c922cc837ee89cb3a",
            "0x81595aea31fbe2a3586b105fe6535fbaa17f1ff7ca53b8bdeb7e990317ade51b",
            "0x0813c8e0372df520de9913802d1d644029570e542c38aa6fae06c54aa518c00b",
            "0x220353b086aa7f1253902444ac7ec33f61380493fe88116da03e7eac5ceb28ba",
            "0x7b10699d5ac4cc268d5d6772144be4966f8e186f027b9f24dcb9583a130b4cdf",
            "0x24be24dc7a716dc828b6e2c583c28f1c270c10d575980e033433ed6216e58966",
            "0x823420e0f2905ac29329736d72c50e910ef340a0738470660c78cd835337e460",
            "0xf070c8a9f21ddb9fae08b4b2be8bd5e86c69d7321fb92d1846d4dfac4e8e7bd8",
            "0xd3e03fc56b91e9ac1313a57f836fa0b6bccedc7f91c448219fb9d5e218fb5a34",
            "0x93d4e5ec82907449729e0121be3ca631b17ae152d6d290a1dc22013f17ec28b9",
            "0xc4c85a88200470c8baab7ad80f112b38f48a3628c2f5859eb9501fc3dec960bd",
            "0x3a109b7d937f1100d778395e83e37082597950c446610c34d8fce96064602de9",
            "0x63860e397c7cb95a5edf8ce043cb79b4163bfb310a7bfe5fafdab8cd7c4281e9",
            "0x4e1f5b6130158582853735faa51c3e013cb6f43aeaa214510d4d098e8b4108a6",
            "0xdd872aca4b4b88853ecb56e439d2526f98be049275a1ff7a94bef1c3a0c3190b",
            "0xcca91015e3d59f141aa210b42f798cbdb3cc92829a8142d3254c3bbeba7a71c1",
            "0x58d94ec4c29954d44013ca9c092deba0faf912d150b8479cbf6930f08eaa9f52",
            "0x24e886f2c4eb0cb6f5820e0c165ccdbb01525bdf473ffc75fdb18850e3ca92a8",
            "0xdddcdc55e10e9c3d0ab4431f8d835f09a46d2ac0d82bb9f93e267e4f72e252a9",
            "0xc348a6d90b7073c7ec26d40e9ffa71fa93ca45eec1804f162f4413fd011c2973",
            "0x8a590a16d1ea99c2d6936f0a5ab4d842f913db92b4b8c9b0a7ba8230cdc06f01",
            "0x1a903c9d8e2928710c90bb39ca944ad69423b41fb3475df033cf4c87847443f1",
            "0xe1895dbecf9e618f263174a6319bc9a7c4339d9d4cc8fc92c060185c336c5813",
            "0xe9e55591b6b01fb67e572a83f0b2a8e4ac42ceac584483c7d9b96eaa2c3ee430",
            "0x52adc318f061d0cd58fe5fe5500dbb7d5be9099cf4a678ceb985a5f1132d82d2",
            "0x6f6ffb20d6af3218cab38a265e0b98454665c2412512a0c1561845479c2f7b24",
            "0x95e7caf724f7a8ac1d67d7a94872dc6d1f1bef86afc01b9a96c2cb49861200da",
            "0xfded0bf2d19f1068334469282268066735dd34788a98d767ada91e661510b7f0",
            "0x6942e924d1fa01a3c2fd61dbe8b4a006df4319c9f579eab2746873271e7b4878",
            "0x87c37807030f0aa91784009c59ce6c15cc2590e9a82f539598fd4ab76674ae20",
            "0x22e90e7603d5caec302861acc61af27593cd27161fc3b265d1b4403cfd21efc6",
            "0x7827e786732c69c7613a2394a3d0b5928ca14119fd01a594763e3ba8b8c962ce",
            "0x955422e9de2575e31562036ffc137fdab3113b25c902499cc444c7f144e0db07",
            "0xdb7f9a4b026fc6326eb38d4d7f5d5d1605f6f01d8e86e7fd58b4ea234300a3c4",
            "0x1a4dce206bcf9218cddcea9fe51469bcd78b636cb08aaf881a97938f166998cf",
            "0xe206b280bea97e8ca01dc1150fc9a976e8abadea461f70748bfb81a7dafa6904",
            "0xb4b3b06ac03576d0ac4091c4d8505548a0dfd31845832b8183bdfb62cf039bee",
            "0xb82b5f2570809aa30184e437543595138742aeaffa8afa8140b29edf03a76745",
            "0x650523dfe93f1fb48ea87771842775f8a79c6c6aad8f0f653f6996efc3351cd6",
            "0x884a4ac888e94a8aa37244a21447d5d267f073856df475a3621ccce1c4a1fe4f",
            "0x0408ab65679f1bd4a22c73f98ec5767cc23b562fc6e7af229e9447f233c61724",
            "0xc4828ebf682bb16dd43b814b3723a8419aa3cc8ea1a27f47386847720c5e9a53",
            "0xc5d53315faf13a0acf8ccd2a91bc928d970814ac4c30779ac1ef7450725aa48b",
            "0xfb3f384d369d01e88837789982c9dae0deb4012a67a03f98f0444344862558f6",
            "0xc6483a1206268e07da21b15101405d0624a0284cba8cbc82455f2e5413cf27b7",
            "0x3753dcd307464f508a211bc93f174198620de07da4a7b04ea9294d970a551a6d",
            "0x1f6be04e25754dc958fc5b279328051f9b6637a36f59ed063b1ebbd371d9cbcc",
            "0x5f46f5188f00d7e6d5a09b1ac5faacf771529b5e82a33b8c2863824444577b83",
            "0x46516f0309babdf7000a3299e2091adfad28e7eaa42957f03d911be1d7bfec3b",
            "0x24ac04b82f87f7a8205667800c13de1481db3333413d8d63624b213050ab80d8",
            "0xaa796f5434af97ce14fceede77dc5821e16d7f6d81d003ac558cee7707491bd0",
            "0x9dea7490058be439a24768610b141b761952f320d20656f9e29a88bebb407f42",
            "0x2d902e270a656e9f07c8c07f31214ad2035c5b175418f9692582d0e0a8d08e6f",
            "0x82ef1792eb066222974eb0a16102c651a03083e5481b0a001b4a99eea10886f6",
            "0x9dc5df831b3d757a330b72a5e0b11bfecdfd5edb00f5bab88dab5ed7f7c14288",
            "0x9ca6268534ee3ab1257e1ec42cf1cd4cb71ce80d8e80e2c40795bb3bdbb257a9",
            "0x51f6b83df97f6fd5a090b9243fb3f9884bb5d4adcc25e990fe074d2e6e86933f",
            "0x31e0ed371e569567de61ff578e3ad92d368400c5619648644cf0f2f64f2ceb43",
            "0x06ec3273a7ce927a94f1a760531ca1827d1bb01092d56ec3c3fd6bb373b8f382",
            "0xf9ec2e910d8a4b64c93d5e6fef442f0155ed408343bfb8a5d5d2a744e92c1e8e",
            "0xa8adb7dee384967ebc56dbd48cd68cba8e8686a57eef9172852c4ac2f394703f",
            "0x0e440bd6cc8a17fb76fee8bb3cfdf6cdb63712f30280b8e7f0b8918a3225fb3c",
            "0x908f76162467e442741a160c4991dd36a4ec4ff7848cff9ea3f7065058c97fba",
            "0x3c88ece853e73930ad44fd5e82e6193a00e0d25328a7023551ab348944dd408f",
            "0x7a447d87b936b1c50cbc0720111cf3cbc3338cad1c1fbafdc909ad65e0b9f1fa",
            "0xa94a705298a8a908647671aaab216dac2c69f1f83eb0321f48a921bccef7e629",
            "0xba870ec4657d1630a671779125dd9af9b70a7d1cde0d4e19759a517accea0888",
            "0xce8242a9c049a49c4c31027ddffbdd617e5a1d503e5b6fc7310df73a87f15162",
            "0x774d04269e8a4450af59468c566b50ced184f65f182416825c306054573f4905",
            "0x13f461ef6492c7e5241c678709fd61f84db56a4f0d23c2d9e469a810bece4fe2",
            "0x0f326621abb4343c0caae5da503c93bc71c20b60ac5c5673bbbbabf451687484",
            "0x69a1740b12e05d68071b0afda6a27d713e9ab597d00e23b1538da51ba5d20768",
            "0x2a00aacf7f420ede34bf16cb9225dfc9220ccab3e6d7a83c3b09302e1e181b15",
            "0xcc6c8352c0bac7db69cf828b0522053ae78b71c7ba2d5244124bf0cbc9f3b331"};*/

    public static class OutboundTest {

        AtomicLong status_sent;
        AtomicLong status_recv;
        AtomicLong bodies_sent;
        AtomicLong bodies_recv;
        AtomicLong bodies_pushed;
        P2pMgr peer;

        List<byte[]> hash_bytes;
        String ip;
        int port;
        Logger log;
        ResBlocksBodies bb_send;

        public OutboundTest(CfgAion cfg,
                            String ip,
                            int port,
                            String[] target,
                            Logger log,
                            AionBlock genesis,
                            List<byte[]> hash_bytes,
                            ResBlocksBodies bb_send) {
            status_sent = new AtomicLong(0);
            status_recv = new AtomicLong(0);
            bodies_sent = new AtomicLong(0);
            bodies_recv = new AtomicLong(0);
            bodies_pushed = new AtomicLong(0);

            this.hash_bytes = hash_bytes;
            this.ip = ip;
            this.port = port;
            this.log = log;
            this.bb_send = bb_send;

            String nodeId = UUID.randomUUID().toString();
            System.out.println(nodeId);

            peer = new P2pMgr(
                    cfg.getNet().getId(),
                    Version.KERNEL_VERSION,
                    nodeId,
                    ip,
                    port,
                    target,
                    false,
                    1,
                    1,
                    false,
                    true,
                    true,
                    false, "",
                    50);

            // focus on requests for now
            List<Handler> cbs = new ArrayList<>();

            // respond to status request to play nice and not get banned
            cbs.add(new ReqStatusHandlerMock(log, port, peer, genesis));
            cbs.add(new ResStatusHandlerMock(log, port, status_recv));
            cbs.add(new ResBlockBodiesHandlerMock(log, port, peer, 6418667, bodies_recv));
            peer.register(cbs);
        }

        public void start() {
            peer.run();
            new Thread(new TaskGetStatusMock(log, port, peer, interval_status, status_sent), "getStatus-"+port).start();

            if (enable_inbound)
                new Thread(new TaskGetBodiesMock(log, port, peer, interval_inbound, hash_bytes, bodies_sent), "getBodies-"+port).start();

            if (enable_outbound)
                new Thread(new TaskSendBodiesMock(log, port, peer, interval_outbound, bb_send, bodies_pushed), "sendBodies-"+port).start();

        }
    }

    public static int interval_inbound;
    public static int interval_status;
    public static int interval_outbound;
    public static boolean enable_outbound;
    public static boolean enable_inbound;


    public static void main(String args[]) throws InterruptedException {

        ECKeyFac.setType(ED25519);
        HashUtil.setType(BLAKE2B_256);

        CfgAion cfg = CfgAion.inst();
        cfg.fromXML();

        List<byte[]> hash_bytes = new ArrayList<>();
        for (String h : known_hashes) {
            hash_bytes.add(ByteUtil.hexStringToBytes(h));
        }

        int start_port = 30305;
        int count = 5;
        String ip = "0.0.0.0";
        String connection = "p2p://c33d391d-a86d-408c-b6f7-13b1c1e810d7@13.95.218.95:30303";
        enable_outbound = false;
        enable_inbound = false;

        if (args != null && args.length > 0) {
            for (int i=0; i< args.length; i++) {
                switch (i) {
                    case 0:
                        if (args[0] != null)
                            ip = args[0] + "";
                        break;
                    case 1:
                        if (args[1] != null)
                            start_port = Integer.parseInt(args[1] + "");
                        break;
                    case 2:
                        if (args[2] != null)
                            count = Integer.parseInt(args[2] + "");
                        break;
                    case 3:
                        if (args[3] != null)
                            connection = args[3] + "";
                        break;
                    case 4:
                        if (args[4] != null)
                            interval_status = Integer.parseInt(args[4] + "");
                        break;
                    case 5:
                        if (args[5] != null)
                            interval_inbound = Integer.parseInt(args[5] + "");
                        break;
                    case 6:
                        if (args[6] != null)
                            interval_outbound = Integer.parseInt(args[6] + "");
                        break;
                    case 7:
                        if (args[7] != null) {
                            String _mode = args[7] + "";
                            if (_mode.equalsIgnoreCase("outbound=true"))
                                enable_outbound = true;
                        }
                        break;
                    case 8:
                        if (args[8] != null) {
                            String _mode = args[8] + "";
                            if (_mode.equalsIgnoreCase("inbound=true"))
                                enable_inbound = true;
                        }
                        break;
                }
            }
        }
        
        try {
            ServiceLoader.load(AionLoggerFactory.class);
        } catch (Exception e) {
            System.out.println("load AionLoggerFactory service fail!" + e.toString());
            throw e;
        }

        // If commit this out, the config setting will be ignore. all log module been set to "INFO" Level
        AionLoggerFactory.init(cfg.getLog().getModules());
        Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        // -------------------------------------------------------------------------------
        // Start p2p Tests
        // -------------------------------------------------------------------------------

        System.out.println("---------------------------------------------");
        System.out.println("Aion p2p Testbench");
        System.out.println("---------------------------------------------");

        // grab a bc instance to easily access databases
        // TODO: swap this out later with specific databases that we need
        AionBlockchainImpl bc = AionBlockchainImpl.inst();

        AionGenesis genconfig = CfgAion.inst().getGenesis();
        AionBlock genesis = new AionBlock(genconfig.getHeader(), genconfig.getTransactionsList());

        //String[] target = new String[] { "p2p://bebecb2d-e7fc-4ab9-b646-feb47d55f7a9@127.0.0.1:30303"};
        String[] target = new String[] { connection };

        // create the blockbodies datastructure
        List<byte[]> blockBodies = new ArrayList<>();
        AionBlock block;
        int out = 0;

        // read from cache, then block store
        for (int i=0; i<300; i++) {
            block = bc.getBlockByNumber(i);
            byte[] blockBytesForadd = block.getEncodedBody();
            if ((out += blockBytesForadd.length) > P2pConstant.MAX_BODY_SIZE) {
                log.debug("<req-blocks-bodies-max-size-reach size={}/{} (whatever ...)>", out, P2pConstant.MAX_BODY_SIZE);
            }
            blockBodies.add(blockBytesForadd);
        }

        ResBlocksBodies bb_send = new ResBlocksBodies(blockBodies);

        List<OutboundTest> tests = new ArrayList<>();

        // just try to bind to "count" contiguous ports ...
        for (int port = start_port; port < start_port + count; port++) {
            if (isOpen(ip, port)) {
                OutboundTest t = new OutboundTest(cfg, ip, port, target, log, genesis, hash_bytes, bb_send);
                t.start();
                tests.add(t);
                log.debug("started process on port: {}", port);
            } else {
                System.out.println("Error: could not bind to " + ip + ":" + port);
            }
        }

        while (true) {
            log.info ("------------------------------------------------------------------------");
            for(OutboundTest test : tests) {
                log.info ("<connection[{}] {} = peers: {}, status: {}/{}, bodies: {}/{} outbound: {} >",
                        test.peer.getNodeId(), test.peer.getConnection(), test.peer.getActiveNodes().size(),
                        test.status_sent.get(), test.status_recv.get(),
                        test.bodies_sent.get(), test.status_recv.get(), test.bodies_pushed.get()
                );
            }
            Thread.sleep(3500);
        }

    }

    private static boolean isOpen(String host, int port) {
        boolean result = true;
        try {
            (new Socket(host, port)).close();
            result = false;
        }
        catch(IOException e) {
            // exception is good?
        }
        return result;
    }
}
