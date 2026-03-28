import 'package:geolocator/geolocator.dart';

class LocationService {
  Future<Position> capturePosition() async {
    final permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied ||
        permission == LocationPermission.deniedForever) {
      throw Exception('Location permission denied');
    }
    return Geolocator.getCurrentPosition();
  }
}
